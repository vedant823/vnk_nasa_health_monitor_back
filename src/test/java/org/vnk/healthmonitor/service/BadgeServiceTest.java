package org.vnk.healthmonitor.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vnk.healthmonitor.domain.dto.Badge;
import org.vnk.healthmonitor.domain.dto.BadgeSet;
import org.vnk.healthmonitor.domain.dto.BadgeStatus;
import org.vnk.healthmonitor.domain.dto.Environment;
import org.vnk.healthmonitor.domain.document.MeasurementSet;
import org.vnk.healthmonitor.domain.document.UserDocument;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for badge evaluation, using the real DataRetrievalService
 * over the JSON fixtures in src/test/resources/data so the badge
 * definitions and measurement ranges stay consistent with the files.
 *
 * Fixture measurement ranges used here:
 * m1 green 4500-11000, m3 green up to 100 (no lower bound),
 * m4 percent-from-baseline green -2% to +3%.
 */
class BadgeServiceTest {

    private static final Instant DAY1 = Instant.parse("2026-01-10T12:00:00Z");
    private static final Instant DAY2 = DAY1.plusSeconds(86400);
    private static final Instant DAY3 = DAY2.plusSeconds(86400);
    private static final Instant DAY4 = DAY3.plusSeconds(86400);

    private BadgeService badgeService;
    private UserDocument user;

    @BeforeEach
    void setUp() {
        DataRetrievalService dataRetrievalService = new DataRetrievalService(
                java.nio.file.Path.of("src", "test", "resources", "data").toString());
        badgeService = new BadgeService(dataRetrievalService);
        user = new UserDocument("astronaut", "hash", "token");
    }

    private void log(String measurementId, Instant time, double value) {
        user.getMeasurementHistory().computeIfAbsent(measurementId, id -> new ArrayList<>())
                .add(new MeasurementSet(measurementId, "user", time, List.of(value), null));
    }

    private Badge badgeByTitle(BadgeSet set, String title) {
        return set.earned().stream()
                .filter(b -> b.title().equals(title))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Badge not earned: " + title));
    }

    private BadgeStatus unearnedByTitle(BadgeSet set, String title) {
        return set.unearned().stream()
                .filter(s -> s.badge().title().equals(title))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Badge not unearned: " + title));
    }

    @Test
    void emptyHistoryLeavesEverythingUnearnedWithZeroProgress() {
        BadgeSet set = badgeService.evaluate(user);

        assertTrue(set.earned().isEmpty());
        assertEquals(7, set.unearned().size());
        assertTrue(set.unearned().stream().allMatch(s ->
                s.progress().stream().allMatch(p -> p == 0.0)));
    }

    @Test
    void plainCountIsSatisfiedByAnyListedMeasurementType() {
        // "One Reading" accepts m1 or m2; the visual m2 counts because isOk is false.
        log("m2", DAY1, 1.0);

        BadgeSet set = badgeService.evaluate(user);

        assertEquals("One Reading", badgeByTitle(set, "One Reading").title());
    }

    @Test
    void distinctDaysCountsEachDayOnlyOnce() {
        // Three same-day readings still leave "Two Days" halfway done.
        log("m1", DAY1, 5200.0);
        log("m1", DAY1.plusSeconds(3600), 5300.0);
        log("m1", DAY1.plusSeconds(7200), 5400.0);

        BadgeStatus status = unearnedByTitle(badgeService.evaluate(user), "Two Days");

        assertEquals(0.5, status.progress().get(0));
    }

    @Test
    void distinctDaysAcrossTwoDaysEarnsTheBadge() {
        log("m1", DAY1, 5200.0);
        log("m1", DAY2, 5300.0);

        BadgeSet set = badgeService.evaluate(user);

        assertEquals("Two Days", badgeByTitle(set, "Two Days").title());
    }

    @Test
    void streakRequiresConsecutiveDaysAndReportsTheBestRun() {
        // Days 1, 2 and 4: the best run is 2 of the required 3.
        log("m1", DAY1, 5200.0);
        log("m1", DAY2, 5200.0);
        log("m1", DAY4, 5200.0);

        BadgeStatus status = unearnedByTitle(badgeService.evaluate(user), "Three Day Streak");

        assertEquals(2.0 / 3.0, status.progress().get(0));
    }

    @Test
    void streakCombinesReadingsOfDifferentMeasurementTypes() {
        // "Three Day Streak" accepts m1 or m3 per day.
        log("m1", DAY1, 5200.0);
        log("m3", DAY2, 50.0);
        log("m1", DAY3, 5200.0);

        BadgeSet set = badgeService.evaluate(user);

        assertEquals("Three Day Streak", badgeByTitle(set, "Three Day Streak").title());
    }

    @Test
    void streakIsNotBrokenByTwoReadingsOnTheSameDay() {
        log("m1", DAY1, 5200.0);
        log("m1", DAY1.plusSeconds(3600), 5200.0);
        log("m1", DAY2, 5200.0);
        log("m1", DAY3, 5200.0);

        BadgeSet set = badgeService.evaluate(user);

        assertEquals("Three Day Streak", badgeByTitle(set, "Three Day Streak").title());
    }

    @Test
    void isOkRejectsOutOfRangeReadings() {
        // 5200 is green for m1, 40000 is dangerous high.
        log("m1", DAY1, 5200.0);
        log("m1", DAY2, 40000.0);

        BadgeStatus status = unearnedByTitle(badgeService.evaluate(user), "Green Days");

        assertEquals(0.5, status.progress().get(0));
    }

    @Test
    void isOkAcceptsGreenReadingsOnDistinctDays() {
        log("m1", DAY1, 5200.0);
        log("m1", DAY2, 5200.0);

        BadgeSet set = badgeService.evaluate(user);

        assertEquals("Green Days", badgeByTitle(set, "Green Days").title());
    }

    @Test
    void isOkUsesTheBaselineAdjustedValue() {
        // m4 is percent-from-baseline: 101 against a 100 baseline is +1%, green.
        user.recordBaselineIfAbsent("m4", 100.0);
        log("m4", DAY1, 101.0);

        BadgeSet set = badgeService.evaluate(user);

        assertEquals("Green Percent", badgeByTitle(set, "Green Percent").title());
    }

    @Test
    void isOkRejectsBaselineAdjustedValuesOutsideTheRange() {
        // +6% from baseline is beyond m4's abnormal and dangerous high.
        user.recordBaselineIfAbsent("m4", 100.0);
        log("m4", DAY1, 106.0);

        BadgeStatus status = unearnedByTitle(badgeService.evaluate(user), "Green Percent");

        assertEquals(0.0, status.progress().get(0));
    }

    @Test
    void visualMeasurementsNeverQualifyForIsOkCriteria() {
        // Visual measurements have no normal range, so they are never green.
        log("m2", DAY1, 1.0);

        BadgeStatus status = unearnedByTitle(badgeService.evaluate(user), "Impossible Visual");

        assertEquals(0.0, status.progress().get(0));
    }

    @Test
    void aBadgeIsEarnedOnlyWhenEveryCriterionIsSatisfied() {
        log("m1", DAY1, 5200.0);

        BadgeStatus status = unearnedByTitle(badgeService.evaluate(user), "Two Criteria");

        // First criterion met, second not.
        assertEquals(1.0, status.progress().get(0));
        assertEquals(0.0, status.progress().get(1));

        log("m3", DAY2, 50.0);
        BadgeSet set = badgeService.evaluate(user);
        assertEquals("Two Criteria", badgeByTitle(set, "Two Criteria").title());
    }

    @Test
    void badgesCarryTheirDescriptionIntoTheResponse() {
        log("m1", DAY1, 5200.0);

        BadgeSet set = badgeService.evaluate(user);

        assertEquals("Log any measurement once.", badgeByTitle(set, "One Reading").description());
    }

    @Test
    void isOkUsesTheEnvironmentRanges() {
        // 4800 is green on the spacecraft (normal starts at 4500) but
        // abnormally low on the Moon (normal starts at 5000 there).
        log("m1", DAY1, 4800.0);
        log("m1", DAY2, 4800.0);

        assertTrue(badgeService.evaluate(user).earned().stream()
                .anyMatch(b -> b.title().equals("Green Days")));

        user.setEnvironment(Environment.MOON);
        BadgeStatus status = unearnedByTitle(badgeService.evaluate(user), "Green Days");
        assertEquals(0.0, status.progress().get(0));
    }

    @Test
    void earnedAndUnearnedAreDisjointAndComplete() {
        log("m1", DAY1, 5200.0);

        BadgeSet set = badgeService.evaluate(user);

        assertEquals(7, set.earned().size() + set.unearned().size());
        assertTrue(set.earned().stream().noneMatch(b ->
                set.unearned().stream().anyMatch(s -> s.badge().title().equals(b.title()))));
    }
}
