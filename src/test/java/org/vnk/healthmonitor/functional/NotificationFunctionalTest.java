package org.vnk.healthmonitor.functional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.vnk.healthmonitor.domain.dto.ReminderDigest;
import org.vnk.healthmonitor.domain.document.UserDocument;
import org.vnk.healthmonitor.repository.UserData;
import org.vnk.healthmonitor.service.NotificationScheduler;
import org.vnk.healthmonitor.service.NotificationSender;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The daily/weekly/monthly notification requirement, verified against the
 * fully booted app: a fresh user (nothing logged yet) has every
 * measurement due now, so everything lands in the daily bucket and the
 * weekly/monthly buckets stay empty (the buckets are disjoint).
 */
class NotificationFunctionalTest extends FunctionalTestBase {

    @Autowired
    UserData userData;

    @Autowired
    NotificationScheduler notificationScheduler;

    @Autowired
    NotificationSender notificationSender;

    @Test
    void freshUserGetsEveryMeasurementInItsSmallestBucket() throws Exception {
        String username = uniqueUsername();
        createAccount(username);
        UserDocument user = userData.findByUsername(username).orElseThrow();

        ReminderDigest digest = notificationScheduler.buildDigest(user);

        // Everything is due now -> everything in daily, nothing else.
        assertTrue(digest.daily().stream().anyMatch(r -> r.measurementId().equals("white_blood_cells")));
        assertTrue(digest.weekly().isEmpty(), "Nothing is due after day 1 for a fresh user");
        assertTrue(digest.monthly().isEmpty(), "Nothing is due after week 1 for a fresh user");
        assertEquals(username, digest.username());
    }

    @Test
    void loggedMeasurementMovesFromDailyToWeekly() throws Exception {
        String username = uniqueUsername();
        String token = createAccount(username);

        // Frequency stays at the 7-day default, so logging now moves the
        // measurement from the daily bucket to the weekly one.
        HttpResponse<String> log = send("POST", "/users/measurements/white_blood_cells",
                "{\"values\":[7000.0]}", authHeader(token));
        assertEquals(200, log.statusCode());

        UserDocument user = userData.findByUsername(username).orElseThrow();
        ReminderDigest digest = notificationScheduler.buildDigest(user);

        assertTrue(digest.daily().stream().noneMatch(r -> r.measurementId().equals("white_blood_cells")));
        assertTrue(digest.weekly().stream().anyMatch(r -> r.measurementId().equals("white_blood_cells")));
        assertTrue(digest.monthly().stream().noneMatch(r -> r.measurementId().equals("white_blood_cells")));
    }

    @Test
    void digestCanBeSentThroughTheSenderWithoutErrors() throws Exception {
        String username = uniqueUsername();
        createAccount(username);
        UserDocument user = userData.findByUsername(username).orElseThrow();

        ReminderDigest digest = notificationScheduler.buildDigest(user);

        assertNotNull(digest);
        // The logging sender must accept any digest (smoke test for the
        // notification pipeline; push/email implement the same interface).
        notificationSender.send(digest);
    }

    @Test
    void digestEndpointServesTheSameBucketsOverHttp() throws Exception {
        String username = uniqueUsername();
        String token = createAccount(username);

        HttpResponse<String> response = send("GET", "/users/digest", null, authHeader(token));
        assertEquals(200, response.statusCode());

        DigestDto digest = parse(response.body(), DigestDto.class);
        assertEquals(username, digest.username());
        // A fresh user is due for everything now: full daily bucket,
        // empty weekly and monthly (the buckets are disjoint).
        assertTrue(digest.daily().stream().anyMatch(r -> r.measurementId().equals("white_blood_cells")));
        assertTrue(digest.weekly().isEmpty());
        assertTrue(digest.monthly().isEmpty());
    }
}
