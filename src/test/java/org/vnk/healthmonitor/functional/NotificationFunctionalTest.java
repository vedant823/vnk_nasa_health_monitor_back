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
 * fully booted app: a fresh user (nothing logged yet) must appear in all
 * three digest buckets, because every measurement is due now.
 */
class NotificationFunctionalTest extends FunctionalTestBase {

    @Autowired
    UserData userData;

    @Autowired
    NotificationScheduler notificationScheduler;

    @Autowired
    NotificationSender notificationSender;

    @Test
    void freshUserGetsEveryMeasurementInAllBuckets() throws Exception {
        String username = uniqueUsername();
        createAccount(username);
        UserDocument user = userData.findByUsername(username).orElseThrow();

        ReminderDigest digest = notificationScheduler.buildDigest(user);

        assertFalse(digest.daily().isEmpty(), "A fresh user is due for every measurement now");
        assertTrue(digest.weekly().containsAll(digest.daily()));
        assertTrue(digest.monthly().containsAll(digest.weekly()));
        assertEquals(username, digest.username());
        assertTrue(digest.daily().stream().anyMatch(r -> r.measurementId().equals("white_blood_cells")));
    }

    @Test
    void loggedMeasurementLeavesTheDailyBucket() throws Exception {
        String username = uniqueUsername();
        String token = createAccount(username);

        // Frequency stays at the 7-day default, so logging now clears the
        // measurement from the daily bucket but keeps it weekly/monthly.
        HttpResponse<String> log = send("POST", "/users/measurements/white_blood_cells",
                "{\"values\":[7000.0]}", authHeader(token));
        assertEquals(200, log.statusCode());

        UserDocument user = userData.findByUsername(username).orElseThrow();
        ReminderDigest digest = notificationScheduler.buildDigest(user);

        assertTrue(digest.daily().stream().noneMatch(r -> r.measurementId().equals("white_blood_cells")));
        assertTrue(digest.weekly().stream().anyMatch(r -> r.measurementId().equals("white_blood_cells")));
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
}
