package org.vnk.healthmonitor.functional;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end officer-child journey: a user becomes a medical officer, a
 * child assigns them at will, the officer reviews the child's data and
 * performs the CMO visual scoring, re-assignment works, and
 * non-children are 403.
 */
class OfficerFunctionalTest extends FunctionalTestBase {

    private static final String VISUAL = "oct_fundus_scan";
    private static final String SANS_RISK = "sans_eye_and_brain_pressure";

    @Test
    void officerSeesReviewsAndScoresTheirChildsData() throws Exception {
        String officerName = uniqueUsername();
        String childName = uniqueUsername();
        String secondOfficerName = uniqueUsername();
        String officerToken = createAccount(officerName);
        String childToken = createAccount(childName);
        String secondOfficerToken = createAccount(secondOfficerName);

        // 1. New accounts are normal users without an officer.
        HttpResponse<String> status = send("GET", "/users/status", null, authHeader(officerToken));
        assertEquals("normal", status.body().replace("\"", "").trim());
        HttpResponse<String> noOfficer = send("GET", "/users/officer", null, authHeader(childToken));
        assertEquals(200, noOfficer.statusCode());
        assertEquals("null", noOfficer.body().replace("\"", "").trim());

        // 2. A user becomes a medical officer while standalone.
        assertEquals(200, send("PUT", "/users/status?newStatus=medical%20officer",
                null, authHeader(officerToken)).statusCode());
        assertEquals(200, send("PUT", "/users/status?newStatus=medical%20officer",
                null, authHeader(secondOfficerToken)).statusCode());
        assertEquals("medical officer", send("GET", "/users/status",
                null, authHeader(officerToken)).body().replace("\"", "").trim());

        // 3. The child assigns the officer at will.
        assertEquals(200, send("PUT", "/users/officer?newOfficer=" + officerName,
                null, authHeader(childToken)).statusCode());
        assertEquals(officerName, send("GET", "/users/officer",
                null, authHeader(childToken)).body().replace("\"", "").trim());

        // 4. The officer sees the child, with no activity yet.
        HttpResponse<String> children = send("GET", "/users/children", null, authHeader(officerToken));
        assertEquals(200, children.statusCode());
        List<ChildOverviewDto> overviews = parseList(children.body(), ChildOverviewDto.class);
        assertEquals(1, overviews.size());
        assertEquals(childName, overviews.get(0).username());
        assertNull(overviews.get(0).latestMeasurement());
        String childId = overviews.get(0).userId();

        // 5. The child logs a visual scan; the officer reads the history.
        assertEquals(200, send("POST", "/users/measurements/" + VISUAL,
                "{\"image\":\"" + TINY_PNG + "\"}", authHeader(childToken)).statusCode());
        HttpResponse<String> history = send("GET", "/users/children/" + childId
                + "/measurements/" + VISUAL, null, authHeader(officerToken));
        assertEquals(200, history.statusCode());
        assertEquals(1, parseList(history.body(), MeasurementSetDto.class).size());

        // 6. A child (with an officer) cannot judge their own images.
        assertEquals(403, send("PUT", "/users/measurements/" + VISUAL + "/score?numAbnormal=2",
                null, authHeader(childToken)).statusCode());

        // 7. The officer performs the CMO review: dangerous (-75).
        assertEquals(200, send("PUT", "/users/children/" + childId
                + "/measurements/" + VISUAL + "/score?numAbnormal=2",
                null, authHeader(officerToken)).statusCode());
        HttpResponse<String> risks = send("GET", "/users/children/" + childId
                + "/risks/" + SANS_RISK, null, authHeader(officerToken));
        assertEquals(25, parse(risks.body(), RiskEvaluationDto.class).score());

        // 8. The child's own view shows the same score — one scoring path.
        HttpResponse<String> own = send("GET", "/users/risks/" + SANS_RISK,
                null, authHeader(childToken));
        assertEquals(25, parse(own.body(), RiskEvaluationDto.class).score());

        // 9. The overdue list works for officers and tags each child.
        HttpResponse<String> overdue = send("GET", "/users/overdue",
                null, authHeader(officerToken));
        assertEquals(200, overdue.statusCode());
        assertTrue(overdue.body().contains("\"childId\":\"" + childId + "\""),
                "Overdue entries must name the child: " + overdue.body());
        assertEquals(403, send("GET", "/users/overdue", null, authHeader(childToken)).statusCode());

        // 10. Another officer (not yet assigned) sees neither the child's
        //     data (403) nor the child in their children list.
        assertEquals(403, send("GET", "/users/children/" + childId + "/risks",
                null, authHeader(secondOfficerToken)).statusCode());
        HttpResponse<String> otherChildren = send("GET", "/users/children",
                null, authHeader(secondOfficerToken));
        assertEquals(0, parseList(otherChildren.body(), ChildOverviewDto.class).size());

        // 11. Re-assignment at will: the child moves to the second officer.
        assertEquals(200, send("PUT", "/users/officer?newOfficer=" + secondOfficerName,
                null, authHeader(childToken)).statusCode());
        assertEquals(403, send("GET", "/users/children/" + childId + "/risks",
                null, authHeader(officerToken)).statusCode());
        HttpResponse<String> nowVisible = send("GET", "/users/children/" + childId + "/risks",
                null, authHeader(secondOfficerToken));
        assertEquals(200, nowVisible.statusCode());

        // 12. Unknown child IDs are 404.
        assertEquals(404, send("GET", "/users/children/nope/risks",
                null, authHeader(officerToken)).statusCode());
    }

    @Test
    void assignmentErrors() throws Exception {
        String childName = uniqueUsername();
        String officerName = uniqueUsername();
        String childToken = createAccount(childName);
        String officerToken = createAccount(officerName);

        // Unknown officer username.
        HttpResponse<String> unknown = send("PUT",
                "/users/officer?newOfficer=nobody-" + UUID.randomUUID(), null, authHeader(childToken));
        assertEquals(404, unknown.statusCode());

        // A user cannot be their own officer.
        HttpResponse<String> self = send("PUT", "/users/officer?newOfficer=" + childName,
                null, authHeader(childToken));
        assertEquals(409, self.statusCode());

        // A normal user cannot take children.
        HttpResponse<String> normalTarget = send("PUT",
                "/users/officer?newOfficer=" + uniqueUsername(), null, authHeader(childToken));
        // (The random username does not exist, so this is 404; the real
        //  normal-target check needs an existing normal user.)
        assertEquals(404, normalTarget.statusCode());
        String normalUserName = uniqueUsername();
        createAccount(normalUserName);
        HttpResponse<String> normal = send("PUT",
                "/users/officer?newOfficer=" + normalUserName, null, authHeader(childToken));
        assertEquals(409, normal.statusCode());

        // No nesting: after childA is assigned, childB cannot name childA
        // as their officer. childA must still be a normal user at this
        // point — the assignment happens first, and promotion afterwards
        // is blocked by the relationship.
        HttpResponse<String> promotion = send("PUT", "/users/status?newStatus=medical%20officer",
                null, authHeader(officerToken));
        assertEquals(200, promotion.statusCode()); // the officer promotes first
        HttpResponse<String> assigned = send("PUT", "/users/officer?newOfficer=" + officerName,
                null, authHeader(childToken));
        assertEquals(200, assigned.statusCode());
        String childBToken = createAccount(uniqueUsername());
        HttpResponse<String> nested = send("PUT", "/users/officer?newOfficer=" + childName,
                null, authHeader(childBToken));
        assertEquals(409, nested.statusCode());

        // The status cannot change while a relationship exists.
        HttpResponse<String> statusWhileAssigned = send("PUT",
                "/users/status?newStatus=normal", null, authHeader(childToken));
        assertEquals(409, statusWhileAssigned.statusCode());

        // A medical officer cannot take an officer.
        HttpResponse<String> officerTakesOfficer = send("PUT",
                "/users/officer?newOfficer=" + childName, null, authHeader(officerToken));
        assertEquals(409, officerTakesOfficer.statusCode());
    }
}
