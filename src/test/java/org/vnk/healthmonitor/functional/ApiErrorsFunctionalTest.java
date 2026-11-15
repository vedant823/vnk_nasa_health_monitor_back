package org.vnk.healthmonitor.functional;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

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
}
