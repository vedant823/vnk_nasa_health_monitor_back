package org.vnk.healthmonitor.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vnk.healthmonitor.domain.dto.Reminder;
import org.vnk.healthmonitor.domain.dto.ReminderDigest;
import org.vnk.healthmonitor.domain.document.UserDocument;
import org.vnk.healthmonitor.repository.UserData;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the digest bucketing: which measurements land in the
 * daily, weekly, and monthly reminder notifications. Uses a fixed Clock
 * so the buckets are deterministic.
 */
class NotificationSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-01-10T12:00:00Z");

    private UserData userData;
    private NotificationSender sender;
    private UserDocument user;
    private NotificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        userData = mock(UserData.class);
        sender = mock(NotificationSender.class);

        DataRetrievalService dataRetrievalService = mock(DataRetrievalService.class);
        when(dataRetrievalService.getMeasurementIds()).thenReturn(List.of("overdue", "due_today", "due_week", "due_month", "beyond"));

        UserReminderService reminderService = new UserReminderService(
                dataRetrievalService, 7, Clock.fixed(NOW, ZoneOffset.UTC));

        user = new UserDocument("astronaut", "hash", "token");
        user.getMeasurementFrequencies().put("overdue", 7);
        user.getMeasurementFrequencies().put("due_today", 7);
        user.getMeasurementFrequencies().put("due_week", 7);
        user.getMeasurementFrequencies().put("due_month", 20);
        user.getMeasurementFrequencies().put("beyond", 40);

        // overdue: taken 14 days ago with a 7-day frequency -> was due 7 days ago.
        user.getMeasurementLastTaken().put("overdue", NOW.minus(Duration.ofDays(14)));
        // due_today: taken 6.5 days ago with a 7-day frequency -> due in half a day.
        user.getMeasurementLastTaken().put("due_today", NOW.minus(Duration.ofHours(156)));
        // due_week: taken 1 day ago with a 7-day frequency -> due in 6 days.
        user.getMeasurementLastTaken().put("due_week", NOW.minus(Duration.ofDays(1)));
        // due_month: taken 5 days ago with a 20-day frequency -> due in 15 days.
        user.getMeasurementLastTaken().put("due_month", NOW.minus(Duration.ofDays(5)));
        // beyond: taken now with a 40-day frequency -> outside the month window.
        user.getMeasurementLastTaken().put("beyond", NOW);

        scheduler = new NotificationScheduler(userData, reminderService, sender,
                Clock.fixed(NOW, ZoneOffset.UTC), true);
    }

    @Test
    void dailyBucketHoldsOverdueAndDueWithinADay() {
        ReminderDigest digest = scheduler.buildDigest(user);

        assertEquals(List.of("overdue", "due_today"),
                digest.daily().stream().map(Reminder::measurementId).toList());
    }

    @Test
    void weeklyBucketHoldsOnlyTheRestOfTheWeek() {
        ReminderDigest digest = scheduler.buildDigest(user);

        // Disjoint buckets: the urgent items live in daily, not weekly.
        assertEquals(List.of("due_week"),
                digest.weekly().stream().map(Reminder::measurementId).toList());
    }

    @Test
    void monthlyBucketHoldsOnlyTheRestOfTheMonth() {
        ReminderDigest digest = scheduler.buildDigest(user);

        assertEquals(List.of("due_month"),
                digest.monthly().stream().map(Reminder::measurementId).toList());
    }

    @Test
    void bucketsAreDisjointAndCoverTheWholeMonth() {
        ReminderDigest digest = scheduler.buildDigest(user);

        List<String> daily = digest.daily().stream().map(Reminder::measurementId).toList();
        List<String> weekly = digest.weekly().stream().map(Reminder::measurementId).toList();
        List<String> monthly = digest.monthly().stream().map(Reminder::measurementId).toList();
        for (String id : List.of("overdue", "due_today", "due_week", "due_month")) {
            long memberships = (daily.contains(id) ? 1 : 0)
                    + (weekly.contains(id) ? 1 : 0)
                    + (monthly.contains(id) ? 1 : 0);
            assertEquals(1, memberships, id + " must be in exactly one bucket");
        }
        assertFalse(daily.stream().anyMatch(weekly::contains));
        assertFalse(daily.stream().anyMatch(monthly::contains));
        assertFalse(weekly.stream().anyMatch(monthly::contains));
    }

    @Test
    void neverTakenMeasurementAppearsOnlyInDaily() {
        UserDocument fresh = new UserDocument("fresh", "hash", "token");
        fresh.getMeasurementFrequencies().put("overdue", 7);

        ReminderDigest digest = scheduler.buildDigest(fresh);

        // Never taken -> due now -> the urgent daily bucket only.
        assertTrue(digest.daily().stream().anyMatch(r -> r.measurementId().equals("overdue")));
        assertFalse(digest.weekly().stream().anyMatch(r -> r.measurementId().equals("overdue")));
        assertFalse(digest.monthly().stream().anyMatch(r -> r.measurementId().equals("overdue")));
    }

    @Test
    void measurementDueExactlyInAWeekLandsInWeekly() {
        UserDocument caught = new UserDocument("caught", "hash", "token");
        caught.getMeasurementFrequencies().put("overdue", 7);
        caught.getMeasurementLastTaken().put("overdue", NOW); // due exactly at the weekly horizon

        ReminderDigest digest = scheduler.buildDigest(caught);

        assertTrue(digest.weekly().stream().anyMatch(r -> r.measurementId().equals("overdue")));
        assertFalse(digest.daily().stream().anyMatch(r -> r.measurementId().equals("overdue")));
    }

    @Test
    void sendDailyDigestsSendsOneDigestPerUser() {
        UserDocument other = new UserDocument("crewmate", "hash", "token");
        when(userData.findAll()).thenReturn(List.of(user, other));

        scheduler.sendDailyDigests();

        verify(sender).send(scheduler.buildDigest(user));
        verify(sender).send(scheduler.buildDigest(other));
    }

    @Test
    void senderFailureDoesNotStopRemainingUsers() {
        UserDocument other = new UserDocument("crewmate", "hash", "token");
        when(userData.findAll()).thenReturn(List.of(user, other));

        var digestUser = scheduler.buildDigest(user);
        org.mockito.Mockito.doThrow(new RuntimeException("channel down"))
                .when(sender).send(digestUser);

        scheduler.sendDailyDigests();

        // The second user still receives their digest.
        verify(sender).send(scheduler.buildDigest(other));
    }

    @Test
    void disabledNotificationsSendNothingButDigestsStayBuildable() {
        UserDocument other = new UserDocument("crewmate", "hash", "token");
        when(userData.findAll()).thenReturn(List.of(user, other));

        NotificationScheduler disabled = new NotificationScheduler(userData,
                new UserReminderService(org.mockito.Mockito.mock(DataRetrievalService.class), 7,
                        Clock.fixed(NOW, ZoneOffset.UTC)),
                sender, Clock.fixed(NOW, ZoneOffset.UTC), false);

        disabled.sendDailyDigests();

        verify(sender, org.mockito.Mockito.never()).send(org.mockito.ArgumentMatchers.any());
        // The digest logic itself stays available (e.g. for GET /users/digest).
        assertEquals("astronaut", disabled.buildDigest(user).username());
    }
}
