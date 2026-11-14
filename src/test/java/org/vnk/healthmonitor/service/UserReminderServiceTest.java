package org.vnk.healthmonitor.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vnk.healthmonitor.domain.dto.Reminder;
import org.vnk.healthmonitor.domain.document.UserDocument;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the due-date math, using a fixed Clock so the results
 * are deterministic.
 */
class UserReminderServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-10T12:00:00Z");

    private DataRetrievalService dataRetrievalService;
    private UserDocument user;

    @BeforeEach
    void setUp() {
        dataRetrievalService = mock(DataRetrievalService.class);
        user = new UserDocument("user", "hash", "token");
        user.getMeasurementFrequencies().put("m1", 7);
        user.getMeasurementFrequencies().put("m2", 3);
        user.getMeasurementFrequencies().put("m3", 20);
        user.getMeasurementFrequencies().put("m4", 40);
        when(dataRetrievalService.getMeasurementIds()).thenReturn(List.of("m1", "m2", "m3", "m4"));
    }

    private UserReminderService service() {
        return new UserReminderService(dataRetrievalService, 7, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void taken(String measurementId, Instant when) {
        user.getMeasurementLastTaken().put(measurementId, when);
    }

    @Test
    void neverTakenMeasurementIsDueNow() {
        List<Reminder> reminders = service().getUpcoming(user, 7);

        Reminder m2 = reminders.stream()
                .filter(r -> r.measurementId().equals("m2"))
                .findFirst().orElseThrow();
        // m2 has a frequency but no lastTaken entry, so it is due now.
        assertEquals(NOW, m2.dueDate());
    }

    @Test
    void unknownFrequencyFallsBackToDefault() {
        // m5 has no frequency entry: the configured default of 7 days applies.
        when(dataRetrievalService.getMeasurementIds()).thenReturn(List.of("m1", "m2", "m3", "m4", "m5"));
        taken("m5", NOW.minus(Duration.ofDays(2)));

        List<Reminder> reminders = service().getUpcoming(user, 7);

        Reminder m5 = reminders.stream()
                .filter(r -> r.measurementId().equals("m5"))
                .findFirst().orElseThrow();
        assertEquals(NOW.plus(Duration.ofDays(5)), m5.dueDate());
    }

    @Test
    void dueDateIsLastTakenPlusFrequency() {
        taken("m1", NOW.minus(Duration.ofDays(3)));

        List<Reminder> reminders = service().getUpcoming(user, 7);

        Reminder m1 = reminders.stream()
                .filter(r -> r.measurementId().equals("m1"))
                .findFirst().orElseThrow();
        assertEquals(NOW.plus(Duration.ofDays(4)), m1.dueDate());
    }

    @Test
    void perMeasurementFrequencyOverrideIsUsed() {
        taken("m2", NOW.minus(Duration.ofDays(1)));

        List<Reminder> reminders = service().getUpcoming(user, 7);

        Reminder m2 = reminders.stream()
                .filter(r -> r.measurementId().equals("m2"))
                .findFirst().orElseThrow();
        assertEquals(NOW.plus(Duration.ofDays(2)), m2.dueDate());
    }

    @Test
    void dueExactlyAtHorizonIsIncluded() {
        // m2 taken 1 day ago with 3-day frequency is due exactly at the
        // 2-day horizon... use withinDays=2 to hit the boundary.
        taken("m2", NOW.minus(Duration.ofDays(1)));

        List<Reminder> reminders = service().getUpcoming(user, 2);

        assertTrue(reminders.stream().anyMatch(r -> r.measurementId().equals("m2")));
    }

    @Test
    void dueAfterHorizonIsExcluded() {
        taken("m4", NOW); // due in 40 days

        List<Reminder> reminders = service().getUpcoming(user, 30);

        assertFalse(reminders.stream().anyMatch(r -> r.measurementId().equals("m4")));
    }

    @Test
    void overdueMeasurementIsIncluded() {
        taken("m1", NOW.minus(Duration.ofDays(14))); // was due 7 days ago

        List<Reminder> reminders = service().getUpcoming(user, 1);

        assertTrue(reminders.stream().anyMatch(r -> r.measurementId().equals("m1")));
    }

    @Test
    void remindersAreOrderedByDueDate() {
        taken("m3", NOW.minus(Duration.ofDays(5)));  // due in 15 days
        taken("m2", NOW.minus(Duration.ofDays(1)));  // due in 2 days
        taken("m1", NOW.minus(Duration.ofDays(6)));  // due in 1 day
        // m4 never taken -> due now (first)

        List<Reminder> reminders = service().getUpcoming(user, 30);

        assertEquals(List.of("m4", "m1", "m2", "m3"),
                reminders.stream().map(Reminder::measurementId).toList());
    }

    @Test
    void dayWindowOnlyContainsDueNowOrOverdue() {
        taken("m1", NOW.minus(Duration.ofDays(1))); // due in 6 days
        taken("m2", NOW.minus(Duration.ofDays(1))); // due in 2 days
        taken("m3", NOW.minus(Duration.ofDays(5))); // due in 15 days
        // m4 never taken -> due now -> the only one inside the day window

        List<Reminder> reminders = service().getUpcoming(user, 1);

        assertEquals(List.of("m4"), reminders.stream().map(Reminder::measurementId).toList());
    }
}
