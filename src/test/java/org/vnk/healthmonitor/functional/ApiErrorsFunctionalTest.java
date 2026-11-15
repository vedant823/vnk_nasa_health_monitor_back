package org.vnk.healthmonitor.functional;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Auth and error-path behavior over real HTTP: 409 for duplicate
 * usernames, 401 for bad credentials and invalid tokens, 404 for unknown
 * IDs. Note: a completely missing Authorization header is a request
 * binding error (400) because the header is required by the controller
 * signature; an invalid token is 401.
 */
class ApiErrorsFunctionalTest extends FunctionalTestBase {

    @Test
    void duplicateUsernameReturns409() throws Exception {
        String username = uniqueUsername();
        createAccount(username);

        HttpResponse<String> duplicate = send("POST", "/users/login",
                "{\"username\":\"" + username + "\",\"password\":\"secret\"}");

        assertEquals(409, duplicate.statusCode());
    }

    @Test
    void loginWithUnknownUserReturns401() throws Exception {
        HttpResponse<String> login = send("PUT", "/users/login",
                "{\"username\":\"ghost-" + uniqueUsername() + "\",\"password\":\"secret\"}");

        assertEquals(401, login.statusCode());
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        String username = uniqueUsername();
        createAccount(username);

        HttpResponse<String> login = send("PUT", "/users/login",
                "{\"username\":\"" + username + "\",\"password\":\"wrong\"}");

        assertEquals(401, login.statusCode());
    }

    @Test
    void missingAuthorizationHeaderReturns400() throws Exception {
        HttpResponse<String> response = send("GET", "/users/risks");

        assertEquals(400, response.statusCode());
    }

    @Test
    void invalidTokenReturns401() throws Exception {
        HttpResponse<String> response = send("GET", "/users/risks",
                null, authHeader("not-a-real-token"));

        assertEquals(401, response.statusCode());
    }

    @Test
    void unknownUserMeasurementReturns404() throws Exception {
        String token = createAccount(uniqueUsername());

        HttpResponse<String> response = send("GET", "/users/measurements/does_not_exist",
                null, authHeader(token));

        assertEquals(404, response.statusCode());
    }

    @Test
    void unknownFrequencyMeasurementReturns404() throws Exception {
        String token = createAccount(uniqueUsername());

        HttpResponse<String> response = send("PUT", "/users/measurements/does_not_exist/frequency",
                "{\"frequencyDays\":3}", authHeader(token));

        assertEquals(404, response.statusCode());
    }

    @Test
    void unknownRiskReturns404() throws Exception {
        String token = createAccount(uniqueUsername());

        HttpResponse<String> response = send("GET", "/users/risks/does_not_exist",
                null, authHeader(token));

        assertEquals(404, response.statusCode());
    }

    @Test
    void unknownDataRiskReturns404() throws Exception {
        HttpResponse<String> response = send("GET", "/data/risks/does_not_exist");

        assertEquals(404, response.statusCode());
    }

    @Test
    void unknownDataMeasurementReturns404() throws Exception {
        HttpResponse<String> response = send("GET", "/data/measurements/does_not_exist");

        assertEquals(404, response.statusCode());
    }

    @Test
    void invalidFrequencyReturns400() throws Exception {
        String token = createAccount(uniqueUsername());

        HttpResponse<String> response = send("PUT", "/users/measurements/white_blood_cells/frequency",
                "{\"frequencyDays\":0}", authHeader(token));

        assertEquals(400, response.statusCode());
    }

    @Test
    void invalidEnvironmentReturns400() throws Exception {
        String token = createAccount(uniqueUsername());

        HttpResponse<String> response = send("PUT", "/users/environment?newEnvironment=spaceship",
                null, authHeader(token));

        assertEquals(400, response.statusCode());
    }

    @Test
    void deleteUnknownMeasurementReturns404() throws Exception {
        String token = createAccount(uniqueUsername());

        HttpResponse<String> response = send("DELETE", "/users/measurements/does_not_exist",
                null, authHeader(token));

        assertEquals(404, response.statusCode());
    }

    @Test
    void deleteWithoutReadingsReturns404() throws Exception {
        String token = createAccount(uniqueUsername());

        HttpResponse<String> response = send("DELETE", "/users/measurements/white_blood_cells",
                null, authHeader(token));

        assertEquals(404, response.statusCode());
    }

    @Test
    void deleteWithNegativeIndexReturns404() throws Exception {
        String token = createAccount(uniqueUsername());
        send("POST", "/users/measurements/white_blood_cells",
                "{\"values\":[5200.0]}", authHeader(token));

        HttpResponse<String> response = send("DELETE",
                "/users/measurements/white_blood_cells?measurementIndex=-1",
                null, authHeader(token));

        assertEquals(404, response.statusCode());
    }

    @Test
    void deleteBaselineReadingWhileOthersExistReturns400() throws Exception {
        String token = createAccount(uniqueUsername());
        // body_mass_change is baseline-relative: the first reading becomes
        // the baseline and must not be deletable while others exist.
        send("POST", "/users/measurements/body_mass_change",
                "{\"values\":[70.0]}", authHeader(token));
        send("POST", "/users/measurements/body_mass_change",
                "{\"values\":[68.0]}", authHeader(token));

        // Index 1 in the newest-first history is the baseline reading.
        HttpResponse<String> response = send("DELETE",
                "/users/measurements/body_mass_change?measurementIndex=1",
                null, authHeader(token));

        assertEquals(400, response.statusCode());
    }

    @Test
    void deleteSoleBaselineReadingSucceedsAndClearsTheBaseline() throws Exception {
        String token = createAccount(uniqueUsername());
        send("POST", "/users/measurements/body_mass_change",
                "{\"values\":[70.0]}", authHeader(token));

        HttpResponse<String> delete = send("DELETE", "/users/measurements/body_mass_change",
                null, authHeader(token));
        assertEquals(200, delete.statusCode());

        HttpResponse<String> history = send("GET", "/users/measurements/body_mass_change",
                null, authHeader(token));
        List<MeasurementSetDto> remaining = parseList(history.body(), MeasurementSetDto.class);
        assertEquals(0, remaining.size());
    }
}
