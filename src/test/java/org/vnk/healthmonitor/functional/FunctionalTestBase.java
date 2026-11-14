package org.vnk.healthmonitor.functional;

import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Base for functional tests: boots the full application (real services,
 * real MongoDB) on a random local port and accesses it over HTTP, exactly
 * like an external client (Hoppscotch, the frontend, curl) would.
 *
 * The database is the Dockerized MongoDB from docker-compose.yml
 * (mongodb://localhost:27017); tests use a dedicated functional database
 * so dev data stays untouched.
 *
 * Tagged "functional" and excluded from the default `test` task; run with
 * `docker-compose up -d` first, then `./gradlew functionalTest`.
 */
@Tag("functional")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class FunctionalTestBase {

    static final String MONGO_URI = "mongodb://localhost:27017/healthmonitor-functional";

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> MONGO_URI);
    }

    @BeforeAll
    static void requireDockerizedMongo() {
        try (var client = MongoClients.create("mongodb://localhost:27017")) {
            client.listDatabaseNames().first();
        } catch (Exception e) {
            fail("MongoDB is not reachable on localhost:27017. "
                    + "Start it first with: docker-compose up -d");
        }
    }

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    /** Response DTOs used to parse JSON, mirroring the API's output records. */
    public record RiskDto(String id, String name, String category,
                          java.util.List<String> measurementIds, String reason) {
    }

    public record InstructionDto(String image, String text) {
    }

    public record MeasurementDto(String id, String name, String category, String unit,
                                 Double dangerousLow, Double abnormalLow,
                                 Double abnormalHigh, Double dangerousHigh,
                                 java.util.List<InstructionDto> instructions) {
    }

    public record ReminderDto(String measurementId, String dueDate) {
    }

    public record RiskEvaluationDto(String riskId, int score,
                                    java.util.List<String> advice, String reason) {
    }

    public record MeasurementSetDto(String measurementId, String userId, String timestamp,
                                    java.util.List<Double> values, java.util.List<String> images) {
    }

    /**
     * Sends an HTTP request to the running app. Supports bodies on any
     * method (including GET, which Hoppscotch desktop also sends for
     * GET /users/login).
     */
    HttpResponse<String> send(String method, String path, String jsonBody,
                              Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (jsonBody != null) {
            builder.method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
            builder.header("Content-Type", "application/json");
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        headers.forEach(builder::header);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> send(String method, String path, String jsonBody) throws Exception {
        return send(method, path, jsonBody, Map.of());
    }

    HttpResponse<String> send(String method, String path) throws Exception {
        return send(method, path, null, Map.of());
    }

    String uniqueUsername() {
        return "astro-" + UUID.randomUUID();
    }

    /** Creates an account and returns its token. */
    String createAccount(String username) throws Exception {
        HttpResponse<String> response = send("POST", "/users/login",
                "{\"username\":\"" + username + "\",\"password\":\"secret\"}");
        assertEquals(200, response.statusCode(), "Account creation failed: " + response.body());
        return parseToken(response.body());
    }

    String parseToken(String body) {
        // The token is returned as a raw string body; tolerate JSON quoting
        // in case the content type negotiation ever changes.
        return body.trim().replace("\"", "");
    }

    Map<String, String> authHeader(String token) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        return headers;
    }

    <T> T parse(String body, Class<T> type) {
        return mapper.readValue(body, type);
    }

    <T> java.util.List<T> parseList(String body, Class<T> elementType) {
        return mapper.readValue(body,
                mapper.getTypeFactory().constructCollectionType(java.util.List.class, elementType));
    }
}
