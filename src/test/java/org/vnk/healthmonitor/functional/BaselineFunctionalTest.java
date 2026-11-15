package org.vnk.healthmonitor.functional;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Baseline measurements, verified against the fully booted app: crew
 * members log plain raw values (e.g. body mass in kilograms) and the
 * backend converts them to percent change (or plain difference) from
 * the first reading before scoring and before serving history.
 */
class BaselineFunctionalTest extends FunctionalTestBase {

    @Test
    void rawBodyMassIsConvertedToPercentChangeAndScored() throws Exception {
        String username = uniqueUsername();
        String token = createAccount(username);

        // 1. First reading: 70 kg. It becomes the user's baseline and
        //    reads back as 0% change.
        HttpResponse<String> baseline = send("POST", "/users/measurements/body_mass_change",
                "{\"values\":[70.0]}", authHeader(token));
        assertEquals(200, baseline.statusCode());

        // 2. Second reading: 66 kg -> (66 - 70) / 70 * 100 = -5.71%,
        //    which is dangerously low (< -5%).
        HttpResponse<String> later = send("POST", "/users/measurements/body_mass_change",
                "{\"values\":[66.0]}", authHeader(token));
        assertEquals(200, later.statusCode());

        // 3. History is served as percent change, newest first.
        HttpResponse<String> history = send("GET", "/users/measurements/body_mass_change",
                null, authHeader(token));
        assertEquals(200, history.statusCode());
        List<MeasurementSetDto> sets = parseList(history.body(), MeasurementSetDto.class);
        assertEquals(2, sets.size());
        assertEquals(-5.714285714285714, sets.get(0).values().get(0), 1e-9);
        assertEquals(0.0, sets.get(1).values().get(0), 1e-9);

        // 4. The nutrition risk (only child: body_mass_change) drops to 25
        //    (dangerous low, -75) and carries the low-side recommendation.
        HttpResponse<String> nutrition = send("GET", "/users/risks/nutrition",
                null, authHeader(token));
        assertEquals(200, nutrition.statusCode());
        RiskEvaluationDto evaluation = parse(nutrition.body(), RiskEvaluationDto.class);
        assertEquals(25, evaluation.score());
        assertTrue(evaluation.advice().stream().anyMatch(a -> a.contains("Body mass is falling")),
                "Expected the low-side recommendation: " + evaluation.advice());

        // 5. A small raw change stays normal: 69 kg is -1.43%.
        HttpResponse<String> small = send("POST", "/users/measurements/body_mass_change",
                "{\"values\":[69.0]}", authHeader(token));
        assertEquals(200, small.statusCode());
        HttpResponse<String> recovered = send("GET", "/users/risks/nutrition",
                null, authHeader(token));
        assertEquals(100, parse(recovered.body(), RiskEvaluationDto.class).score());

        // 6. The trend compares the two most recent baseline-adjusted
        //    readings: -5.7% (66 kg) -> -1.4% (69 kg) is upward — recovery.
        HttpResponse<String> trend = send("GET", "/users/measurements/body_mass_change/trend",
                null, authHeader(token));
        assertEquals(200, trend.statusCode());
        assertTrue(trend.body().contains("\"up\""), "Unexpected trend body: " + trend.body());
    }

    @Test
    void retinalThicknessUsesDifferenceFromBaseline() throws Exception {
        String username = uniqueUsername();
        String token = createAccount(username);

        // Baseline 400 micrometres, then 440: a +40 difference, which is
        // abnormal high (> 20) but not dangerous (< 55) -> score 60.
        assertEquals(200, send("POST", "/users/measurements/retinal_thickness_change",
                "{\"values\":[400.0]}", authHeader(token)).statusCode());
        assertEquals(200, send("POST", "/users/measurements/retinal_thickness_change",
                "{\"values\":[440.0]}", authHeader(token)).statusCode());

        HttpResponse<String> sans = send("GET", "/users/risks/sans_eye_and_brain_pressure",
                null, authHeader(token));
        assertEquals(200, sans.statusCode());
        RiskEvaluationDto evaluation = parse(sans.body(), RiskEvaluationDto.class);
        assertEquals(60, evaluation.score());
        assertTrue(evaluation.advice().stream().anyMatch(a -> a.contains("Retina is thickening")),
                "Expected the high-side recommendation: " + evaluation.advice());
        // No OCT reading was logged, so the visual measurement is skipped
        // entirely: the reason describes only the retinal violation.
        assertTrue(evaluation.reason().contains("Retinal Thickness Change"),
                "Unexpected reason: " + evaluation.reason());
        assertTrue(!evaluation.reason().contains("not yet scored"),
                "Nothing to review, so no pending-review note: " + evaluation.reason());
    }

    @Test
    void trendIsDownWhenBaselineAdjustedValuesFall() throws Exception {
        String username = uniqueUsername();
        String token = createAccount(username);

        // Memory score: baseline 100, then 90 -> 0% -> -10% is downward.
        assertEquals(200, send("POST", "/users/measurements/memory_score_change",
                "{\"values\":[100.0]}", authHeader(token)).statusCode());
        assertEquals(200, send("POST", "/users/measurements/memory_score_change",
                "{\"values\":[90.0]}", authHeader(token)).statusCode());

        HttpResponse<String> trend = send("GET", "/users/measurements/memory_score_change/trend",
                null, authHeader(token));
        assertEquals(200, trend.statusCode());
        assertTrue(trend.body().contains("\"down\""), "Unexpected trend body: " + trend.body());
    }

    @Test
    void absoluteMeasurementsAreUnaffectedByBaselines() throws Exception {
        String username = uniqueUsername();
        String token = createAccount(username);

        // White blood cells are absolute: the raw value is scored as-is.
        assertEquals(200, send("POST", "/users/measurements/white_blood_cells",
                "{\"values\":[5200.0]}", authHeader(token)).statusCode());

        HttpResponse<String> history = send("GET", "/users/measurements/white_blood_cells",
                null, authHeader(token));
        List<MeasurementSetDto> sets = parseList(history.body(), MeasurementSetDto.class);
        assertEquals(List.of(5200.0), sets.get(0).values());

        HttpResponse<String> immune = send("GET", "/users/risks/immune_dysregulation",
                null, authHeader(token));
        assertEquals(100, parse(immune.body(), RiskEvaluationDto.class).score());
    }
}
