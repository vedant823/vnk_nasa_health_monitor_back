package org.vnk.healthmonitor.functional;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end journey of one user through every user endpoint, exactly as
 * the frontend or Hoppscotch would call them.
 */
class UserJourneyFunctionalTest extends FunctionalTestBase {

    @Test
    void fullJourneyFromAccountCreationToRiskScores() throws Exception {
        // 1. Create an account and get a token.
        String username = uniqueUsername();
        String token = createAccount(username);

        // 2. Log in with the same credentials and get the same token.
        HttpResponse<String> login = send("PUT", "/users/login",
                "{\"username\":\"" + username + "\",\"password\":\"secret\"}");
        assertEquals(200, login.statusCode());
        assertEquals(token, parseToken(login.body()));

        // 3. Static data is served without authentication.
        HttpResponse<String> risksData = send("GET", "/data/risks");
        assertEquals(200, risksData.statusCode());
        List<RiskDto> risks = parseList(risksData.body(), RiskDto.class);
        assertTrue(risks.stream().anyMatch(r -> r.id().equals("immune_dysregulation")));

        HttpResponse<String> measurementData = send("GET", "/data/measurements/white_blood_cells");
        assertEquals(200, measurementData.statusCode());
        MeasurementDto measurement = parse(measurementData.body(), MeasurementDto.class);
        assertNotNull(measurement.instructions());
        assertTrue(measurement.instructions().size() > 0);

        // 4. Log a normal measurement.
        HttpResponse<String> logNormal = send("POST", "/users/measurements/white_blood_cells",
                "{\"values\":[5200.0]}", authHeader(token));
        assertEquals(200, logNormal.statusCode());

        // 5. The history contains the logged value.
        HttpResponse<String> history = send("GET", "/users/measurements/white_blood_cells",
                null, authHeader(token));
        assertEquals(200, history.statusCode());
        List<MeasurementSetDto> sets = parseList(history.body(), MeasurementSetDto.class);
        assertEquals(1, sets.size());
        assertEquals(List.of(5200.0), sets.get(0).values());

        // 6. Change the reminder frequency to every 3 days.
        HttpResponse<String> frequency = send("PUT", "/users/measurements/white_blood_cells/frequency",
                "{\"frequencyDays\":3}", authHeader(token));
        assertEquals(200, frequency.statusCode());

        // 7. The measurement shows up in the upcoming reminders.
        HttpResponse<String> upcoming = send("GET", "/users/upcoming?withinDays=30",
                null, authHeader(token));
        assertEquals(200, upcoming.statusCode());
        List<ReminderDto> reminders = parseList(upcoming.body(), ReminderDto.class);
        assertTrue(reminders.stream().anyMatch(r -> r.measurementId().equals("white_blood_cells")));

        // 8. All risks score 100 after only a normal measurement.
        HttpResponse<String> allRisks = send("GET", "/users/risks", null, authHeader(token));
        assertEquals(200, allRisks.statusCode());
        List<RiskEvaluationDto> evaluations = parseList(allRisks.body(), RiskEvaluationDto.class);
        assertEquals(risks.size(), evaluations.size());
        assertTrue(evaluations.stream().allMatch(e -> e.score() == 100));

        // 9. An abnormal reading lowers the immune risk score to 60
        //    (abnormal high, -40) and returns the high recommendation.
        HttpResponse<String> logAbnormal = send("POST", "/users/measurements/white_blood_cells",
                "{\"values\":[20000.0]}", authHeader(token));
        assertEquals(200, logAbnormal.statusCode());

        HttpResponse<String> immune = send("GET", "/users/risks/immune_dysregulation",
                null, authHeader(token));
        assertEquals(200, immune.statusCode());
        RiskEvaluationDto evaluation = parse(immune.body(), RiskEvaluationDto.class);
        assertEquals(60, evaluation.score());
        assertTrue(evaluation.advice().stream()
                .anyMatch(a -> a.contains("High white cell count")));
        assertTrue(evaluation.reason().contains("above the normal range"));
    }
}
