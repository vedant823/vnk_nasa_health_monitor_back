package org.vnk.healthmonitor.functional;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The badge journey over real HTTP: a fresh account starts with every
 * badge unearned at zero progress, and logging qualifying readings moves
 * badges into the earned list.
 */
class BadgesFunctionalTest extends FunctionalTestBase {

    record CriterionDto(List<String> measurementType, boolean distinctDays,
                        boolean streak, boolean isOk, int count) {
    }

    record BadgeDto(String title, List<CriterionDto> criteria) {
    }

    record BadgeStatusDto(BadgeDto badge, List<Double> progress) {
    }

    record BadgeSetDto(List<BadgeDto> earned, List<BadgeStatusDto> unearned) {
    }

    @Test
    void badgesMoveFromUnearnedToEarnedAsReadingsAreLogged() throws Exception {
        String token = createAccount(uniqueUsername());

        // 1. A fresh account has every badge unearned at zero progress.
        HttpResponse<String> initial = send("GET", "/users/badges", null, authHeader(token));
        assertEquals(200, initial.statusCode());
        BadgeSetDto before = parse(initial.body(), BadgeSetDto.class);
        assertTrue(before.earned().isEmpty());
        assertTrue(before.unearned().size() >= 8, "Expected the real badges.json content");
        assertTrue(before.unearned().stream().allMatch(s ->
                s.progress().stream().allMatch(p -> p == 0.0)));

        // 2. One normal white cell reading earns "First Check-in" (any
        //    measurement counts; isOk is false for that badge).
        HttpResponse<String> logReading = send("POST", "/users/measurements/white_blood_cells",
                "{\"values\":[5200.0]}", authHeader(token));
        assertEquals(200, logReading.statusCode());

        HttpResponse<String> afterOne = send("GET", "/users/badges", null, authHeader(token));
        BadgeSetDto afterFirst = parse(afterOne.body(), BadgeSetDto.class);
        assertTrue(afterFirst.earned().stream().anyMatch(b -> b.title().equals("First Check-in")));
        assertTrue(afterFirst.unearned().stream().noneMatch(s ->
                s.badge().title().equals("First Check-in")));

        // 3. Two green sleep readings (7-10 hours) earn "Deep Sleeper".
        send("POST", "/users/measurements/sleep_duration",
                "{\"values\":[8.0]}", authHeader(token));
        send("POST", "/users/measurements/sleep_duration",
                "{\"values\":[8.5]}", authHeader(token));

        HttpResponse<String> afterSleep = send("GET", "/users/badges", null, authHeader(token));
        BadgeSetDto afterSleepLog = parse(afterSleep.body(), BadgeSetDto.class);
        assertTrue(afterSleepLog.earned().stream().anyMatch(b -> b.title().equals("Deep Sleeper")));

        // 4. "Week Streak" needs 7 consecutive days, so it stays unearned —
        //    but the white cell reading already counts as its first day.
        BadgeStatusDto weekStreak = afterSleepLog.unearned().stream()
                .filter(s -> s.badge().title().equals("Week Streak"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Week Streak missing from unearned"));
        assertEquals(1.0 / 7.0, weekStreak.progress().get(0));
    }
}
