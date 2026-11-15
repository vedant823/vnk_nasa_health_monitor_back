package org.vnk.healthmonitor.functional;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The API must be usable from Hoppscotch (https://hoppscotch.io), so CORS
 * must accept its origin — including the preflight for the Authorization
 * header used on the user endpoints.
 */
class CorsFunctionalTest extends FunctionalTestBase {

    private static final String HOPPSCOTCH = "https://hoppscotch.io";

    @Test
    void actualRequestFromHoppscotchOriginGetsAllowOriginHeader() throws Exception {
        HttpResponse<String> response = send("GET", "/data/risks", null,
                Map.of("Origin", HOPPSCOTCH));

        assertEquals(200, response.statusCode());
        assertEquals(HOPPSCOTCH,
                response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
    }

    @Test
    void preflightForGetWithAuthorizationIsAccepted() throws Exception {
        HttpResponse<String> preflight = send("OPTIONS", "/users/risks", null, Map.of(
                "Origin", HOPPSCOTCH,
                "Access-Control-Request-Method", "GET",
                "Access-Control-Request-Headers", "authorization"));

        assertEquals(200, preflight.statusCode());
        assertEquals(HOPPSCOTCH,
                preflight.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
        assertTrue(preflight.headers().firstValue("Access-Control-Allow-Methods")
                        .orElse("").contains("GET"));
        assertTrue(preflight.headers().firstValue("Access-Control-Allow-Headers")
                        .orElse("").toLowerCase().contains("authorization"));
    }

    @Test
    void preflightForFrequencyUpdateIsAccepted() throws Exception {
        HttpResponse<String> preflight = send("OPTIONS", "/users/measurements/m1/frequency", null, Map.of(
                "Origin", HOPPSCOTCH,
                "Access-Control-Request-Method", "PUT",
                "Access-Control-Request-Headers", "authorization,content-type"));

        assertEquals(200, preflight.statusCode());
        assertTrue(preflight.headers().firstValue("Access-Control-Allow-Methods")
                        .orElse("").contains("PUT"));
    }

    @Test
    void preflightForDeleteIsAccepted() throws Exception {
        HttpResponse<String> preflight = send("OPTIONS", "/users/measurements/m1", null, Map.of(
                "Origin", HOPPSCOTCH,
                "Access-Control-Request-Method", "DELETE",
                "Access-Control-Request-Headers", "authorization"));

        assertEquals(200, preflight.statusCode());
        assertEquals(HOPPSCOTCH,
                preflight.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
        assertTrue(preflight.headers().firstValue("Access-Control-Allow-Methods")
                .orElse("").contains("DELETE"));
    }

    @Test
    void otherOriginsAreRejected() throws Exception {
        HttpResponse<String> preflight = send("OPTIONS", "/data/risks", null, Map.of(
                "Origin", "https://evil.example.com",
                "Access-Control-Request-Method", "GET"));

        assertEquals(403, preflight.statusCode());
    }
}
