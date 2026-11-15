package org.vnk.healthmonitor.functional;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end visual measurement scoring: the flags checklist, the CMO
 * score entry, and its effect on the risk score, exactly as the
 * frontend or Hoppscotch would call them.
 */
class VisualScoringFunctionalTest extends FunctionalTestBase {

    private static final String VISUAL = "oct_fundus_scan";
    private static final String NUMERICAL = "white_blood_cells";
    private static final String SANS_RISK = "sans_eye_and_brain_pressure";

    @Test
    void flagsScoreEntryAndRiskEffect() throws Exception {
        String token = createAccount(uniqueUsername());

        // 1. The flags checklist is served without authentication.
        HttpResponse<String> flags = send("GET", "/data/measurements/" + VISUAL + "/flags");
        assertEquals(200, flags.statusCode());
        List<String> flagList = parseList(flags.body(), String.class);
        assertTrue(flagList.size() > 0, "The visual measurement should have flags");

        // 2. A visual measurement is logged with an image (base64).
        HttpResponse<String> log = send("POST", "/users/measurements/" + VISUAL,
                "{\"image\":\"aGVsbG8=\"}", authHeader(token));
        assertEquals(200, log.statusCode());

        // 3. An unreviewed visual reading is noted but not penalized.
        HttpResponse<String> unscored = send("GET", "/users/risks/" + SANS_RISK,
                null, authHeader(token));
        assertEquals(200, unscored.statusCode());
        RiskEvaluationDto unscoredEvaluation = parse(unscored.body(), RiskEvaluationDto.class);
        assertEquals(100, unscoredEvaluation.score());
        assertTrue(unscoredEvaluation.reason().contains("not yet scored"),
                "Unexpected reason: " + unscoredEvaluation.reason());

        // 4. The CMO records two observed abnormalities -> dangerous (-75).
        HttpResponse<String> score = send("PUT", "/users/measurements/" + VISUAL
                + "/score?numAbnormal=2", null, authHeader(token));
        assertEquals(200, score.statusCode());

        // 5. The score is stored on the reading and visible in the history.
        HttpResponse<String> history = send("GET", "/users/measurements/" + VISUAL,
                null, authHeader(token));
        assertEquals(200, history.statusCode());
        List<MeasurementSetDto> sets = parseList(history.body(), MeasurementSetDto.class);
        assertEquals(1, sets.size());
        assertEquals(2, sets.get(0).numAbnormal());

        // 6. The risk score reflects the dangerous visual reading.
        HttpResponse<String> scored = send("GET", "/users/risks/" + SANS_RISK,
                null, authHeader(token));
        assertEquals(200, scored.statusCode());
        RiskEvaluationDto scoredEvaluation = parse(scored.body(), RiskEvaluationDto.class);
        assertEquals(25, scoredEvaluation.score());
        assertTrue(scoredEvaluation.reason().contains("2 abnormalities"),
                "Unexpected reason: " + scoredEvaluation.reason());
        assertTrue(scoredEvaluation.advice().stream().anyMatch(a -> a.contains("Crew Medical Officer")),
                "Advice should mention the CMO: " + scoredEvaluation.advice());

        // 7. Re-scoring to zero abnormalities restores the full score.
        HttpResponse<String> rescore = send("PUT", "/users/measurements/" + VISUAL
                + "/score?numAbnormal=0", null, authHeader(token));
        assertEquals(200, rescore.statusCode());
        HttpResponse<String> cleared = send("GET", "/users/risks/" + SANS_RISK,
                null, authHeader(token));
        assertEquals(100, parse(cleared.body(), RiskEvaluationDto.class).score());
    }

    @Test
    void scoringErrors() throws Exception {
        String token = createAccount(uniqueUsername());

        // Numerical measurements cannot be scored.
        HttpResponse<String> numerical = send("POST", "/users/measurements/" + NUMERICAL,
                "{\"values\":[5200.0]}", authHeader(token));
        assertEquals(200, numerical.statusCode());
        assertEquals(400, send("PUT", "/users/measurements/" + NUMERICAL + "/score?numAbnormal=1",
                null, authHeader(token)).statusCode());

        // A visual measurement with no readings is a 404.
        assertEquals(404, send("PUT", "/users/measurements/" + VISUAL + "/score?numAbnormal=1",
                null, authHeader(token)).statusCode());

        // A negative abnormality count is invalid.
        HttpResponse<String> log = send("POST", "/users/measurements/" + VISUAL,
                "{\"image\":\"aGVsbG8=\"}", authHeader(token));
        assertEquals(200, log.statusCode());
        assertEquals(400, send("PUT", "/users/measurements/" + VISUAL + "/score?numAbnormal=-1",
                null, authHeader(token)).statusCode());

        // Unknown measurements and a bad index are 404s.
        assertEquals(404, send("PUT", "/users/measurements/nope/score?numAbnormal=1",
                null, authHeader(token)).statusCode());
        assertEquals(404, send("PUT", "/users/measurements/" + VISUAL + "/score?numAbnormal=1&measurementIndex=5",
                null, authHeader(token)).statusCode());

        // Flags: 400 for numerical, 404 for unknown.
        assertEquals(400, send("GET", "/data/measurements/" + NUMERICAL + "/flags").statusCode());
        assertEquals(404, send("GET", "/data/measurements/nope/flags").statusCode());
    }

    @Test
    void scoreEntryByIndexTargetsTheRightReading() throws Exception {
        String token = createAccount(uniqueUsername());

        // Two readings; the second (older) one is scored through its index.
        assertEquals(200, send("POST", "/users/measurements/" + VISUAL,
                "{\"image\":\"aGk=\"}", authHeader(token)).statusCode());
        assertEquals(200, send("POST", "/users/measurements/" + VISUAL,
                "{\"image\":\"eW8=\"}", authHeader(token)).statusCode());

        assertEquals(200, send("PUT", "/users/measurements/" + VISUAL
                + "/score?numAbnormal=1&measurementIndex=1", null, authHeader(token)).statusCode());

        HttpResponse<String> history = send("GET", "/users/measurements/" + VISUAL,
                null, authHeader(token));
        List<MeasurementSetDto> sets = parseList(history.body(), MeasurementSetDto.class);
        // History is newest-first: index 1 is the older reading.
        assertNull(sets.get(0).numAbnormal());
        assertEquals(1, sets.get(1).numAbnormal());
    }
}
