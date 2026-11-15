package org.vnk.healthmonitor.functional;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The environment journey over real HTTP: new accounts start on the
 * spacecraft, PUT switches the environment (rejecting unknown values),
 * and GET reflects the persisted choice across requests.
 */
class EnvironmentFunctionalTest extends FunctionalTestBase {

    @Test
    void environmentSwitchesAndPersists() throws Exception {
        String token = createAccount(uniqueUsername());

        // 1. New accounts start on the spacecraft.
        HttpResponse<String> initial = send("GET", "/users/environment", null, authHeader(token));
        assertEquals(200, initial.statusCode());
        assertEquals("spacecraft", initial.body().trim());

        // 2. Switching to the Mars base persists across requests.
        HttpResponse<String> put = send("PUT", "/users/environment?newEnvironment=mars",
                null, authHeader(token));
        assertEquals(200, put.statusCode());

        HttpResponse<String> afterMars = send("GET", "/users/environment", null, authHeader(token));
        assertEquals("mars", afterMars.body().trim());

        // 3. Back to the station, then to the Moon.
        send("PUT", "/users/environment?newEnvironment=station", null, authHeader(token));
        HttpResponse<String> afterStation = send("GET", "/users/environment", null, authHeader(token));
        assertEquals("station", afterStation.body().trim());

        HttpResponse<String> putMoon = send("PUT", "/users/environment?newEnvironment=moon",
                null, authHeader(token));
        assertEquals(200, putMoon.statusCode());
        HttpResponse<String> afterMoon = send("GET", "/users/environment", null, authHeader(token));
        assertEquals("moon", afterMoon.body().trim());

        // 4. Unknown environments are rejected without changing the value.
        HttpResponse<String> invalid = send("PUT", "/users/environment?newEnvironment=spaceship",
                null, authHeader(token));
        assertEquals(400, invalid.statusCode());
        HttpResponse<String> unchanged = send("GET", "/users/environment", null, authHeader(token));
        assertEquals("moon", unchanged.body().trim());
    }
}
