package org.vnk.healthmonitor.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.vnk.healthmonitor.domain.dto.BaselineMode;
import org.vnk.healthmonitor.domain.dto.Environment;
import org.vnk.healthmonitor.domain.dto.LogMeasurementRequest;
import org.vnk.healthmonitor.domain.dto.Measurement;
import org.vnk.healthmonitor.domain.dto.MeasurementCategory;
import org.vnk.healthmonitor.domain.dto.MeasurementTrend;
import org.vnk.healthmonitor.domain.dto.Range;
import org.vnk.healthmonitor.domain.dto.TrendDirection;
import org.vnk.healthmonitor.domain.document.MeasurementSet;
import org.vnk.healthmonitor.domain.document.UserDocument;
import org.vnk.healthmonitor.repository.UserData;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for logging measurements and reading them back: baseline
 * recording, raw-to-change conversion, trend direction, and validation.
 */
class UserDataServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-10T12:00:00Z");

    private UserData userData;
    private DataRetrievalService dataRetrievalService;
    private UserDataService userDataService;
    private UserDocument user;

    @BeforeEach
    void setUp() {
        userData = mock(UserData.class);
        dataRetrievalService = mock(DataRetrievalService.class);
        userDataService = new UserDataService(userData, dataRetrievalService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        user = new UserDocument("astronaut", "hash", "token");
    }

    private void givenMeasurement(Measurement measurement) {
        when(dataRetrievalService.getMeasurement(measurement.id())).thenReturn(measurement);
    }

    private Measurement percentMeasurement(String id) {
        return new Measurement(id, id, MeasurementCategory.NUMERICAL, "kg",
                uniformRanges(-5.0, -2.0, 3.0, 5.0), true, BaselineMode.PERCENT, null, null, List.of());
    }

    private Measurement plainMeasurement(String id) {
        return new Measurement(id, id, MeasurementCategory.NUMERICAL, "units",
                uniformRanges(3000.0, 4500.0, 11000.0, 30000.0), false, BaselineMode.NONE, null, null, List.of());
    }

    private static List<Range> uniformRanges(Double dangerousLow, Double abnormalLow,
                                             Double abnormalHigh, Double dangerousHigh) {
        return java.util.Arrays.stream(Environment.values())
                .map(env -> new Range(env, dangerousLow, abnormalLow, abnormalHigh, dangerousHigh))
                .toList();
    }

    @Test
    void firstLogRecordsTheBaselineAndLaterLogsKeepIt() {
        givenMeasurement(percentMeasurement("body_mass"));
        userDataService.logMeasurement(user, "body_mass", new LogMeasurementRequest(List.of(70.0), null));
        userDataService.logMeasurement(user, "body_mass", new LogMeasurementRequest(List.of(66.0), null));

        assertEquals(70.0, user.baselineOf("body_mass"));
    }

    @Test
    void baselineUsesTheAverageOfMultipleValues() {
        givenMeasurement(percentMeasurement("m"));
        userDataService.logMeasurement(user, "m", new LogMeasurementRequest(List.of(68.0, 72.0), null));

        assertEquals(70.0, user.baselineOf("m"));
    }

    @Test
    void historyIsReturnedNewestFirstAndConvertedToPercentChange() {
        givenMeasurement(percentMeasurement("body_mass"));
        user.recordBaselineIfAbsent("body_mass", 70.0);
        // Older reading first, then the newer one (as production would store them).
        user.getMeasurementHistory().computeIfAbsent("body_mass", id -> new java.util.ArrayList<>())
                .add(new MeasurementSet("body_mass", "user", NOW.minusSeconds(86400), List.of(70.0), null));
        user.getMeasurementHistory().computeIfAbsent("body_mass", id -> new java.util.ArrayList<>())
                .add(new MeasurementSet("body_mass", "user", NOW, List.of(66.0), null));

        List<MeasurementSet> history = userDataService.getMeasurements(user, "body_mass");

        assertEquals(2, history.size());
        // Newest first: 66 kg against a 70 kg baseline is -5.71%.
        assertEquals(-5.714285714285714, history.get(0).getValues().get(0));
        // The first reading is the baseline itself, so 0% change.
        assertEquals(0.0, history.get(1).getValues().get(0));
    }

    @Test
    void plainMeasurementsKeepTheirRawValues() {
        givenMeasurement(plainMeasurement("wbc"));
        user.getMeasurementHistory().computeIfAbsent("wbc", id -> new java.util.ArrayList<>())
                .add(new MeasurementSet("wbc", "user", NOW.minusSeconds(86400), List.of(7000.0), null));
        user.getMeasurementHistory().computeIfAbsent("wbc", id -> new java.util.ArrayList<>())
                .add(new MeasurementSet("wbc", "user", NOW, List.of(5200.0), null));

        List<MeasurementSet> history = userDataService.getMeasurements(user, "wbc");

        assertEquals(List.of(5200.0), history.get(0).getValues());
        assertEquals(List.of(7000.0), history.get(1).getValues());
        assertNull(user.baselineOf("wbc"));
    }

    @Test
    void relativeMeasurementWithoutBaselineFallsBackToRawValues() {
        // Legacy data: history exists but no baseline was ever recorded.
        givenMeasurement(percentMeasurement("m"));
        user.getMeasurementHistory().computeIfAbsent("m", id -> new java.util.ArrayList<>())
                .add(new MeasurementSet("m", "user", NOW, List.of(-6.0), null));

        List<MeasurementSet> history = userDataService.getMeasurements(user, "m");

        assertEquals(List.of(-6.0), history.get(0).getValues());
    }

    @Test
    void loggingUpdatesLastTakenAndSaves() {
        givenMeasurement(plainMeasurement("wbc"));

        userDataService.logMeasurement(user, "wbc", new LogMeasurementRequest(List.of(7000.0), null));

        assertEquals(NOW, user.lastTakenOf("wbc"));
        verify(userData).save(user);
    }

    @Test
    void numericalMeasurementRequiresValues() {
        givenMeasurement(plainMeasurement("wbc"));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> userDataService.logMeasurement(user, "wbc", new LogMeasurementRequest(null, null)));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void negativeValueRejectedWhenMeasurementCannotBeNegative() {
        givenMeasurement(plainMeasurement("wbc"));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> userDataService.logMeasurement(user, "wbc", new LogMeasurementRequest(List.of(-1.0), null)));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void visualMeasurementRequiresAnImage() {
        givenMeasurement(new Measurement("oct", "oct", MeasurementCategory.VISUAL, null,
                uniformRanges(null, null, null, null), false, BaselineMode.NONE, null, null, List.of()));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> userDataService.logMeasurement(user, "oct", new LogMeasurementRequest(null, "  ")));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void unknownMeasurementThrows404() {
        when(dataRetrievalService.getMeasurement("nope")).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown measurement: nope"));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> userDataService.getMeasurements(user, "nope"));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void setFrequencyStoresTheNewValueAndSaves() {
        givenMeasurement(plainMeasurement("wbc"));

        userDataService.setFrequency(user, "wbc", 3);

        assertEquals(3, user.frequencyOf("wbc"));
        verify(userData).save(user);
    }

    @Test
    void setEnvironmentStoresTheValueAndSaves() {
        userDataService.setEnvironment(user, "mars");

        assertEquals(Environment.MARS, user.environmentOf());
        verify(userData).save(user);
    }

    @Test
    void setEnvironmentIsCaseInsensitive() {
        userDataService.setEnvironment(user, "Moon");

        assertEquals(Environment.MOON, user.environmentOf());
    }

    @Test
    void unknownEnvironmentReturns400() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> userDataService.setEnvironment(user, "spaceship"));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void deleteDefaultsToTheMostRecentReading() {
        givenMeasurement(plainMeasurement("wbc"));
        seedHistory("wbc", NOW.minusSeconds(86400), NOW);

        userDataService.deleteMeasurement(user, "wbc", null);

        List<MeasurementSet> stored = user.getMeasurementHistory().get("wbc");
        assertEquals(1, stored.size());
        assertEquals(NOW.minusSeconds(86400), stored.get(0).getTimestamp());
        verify(userData).save(user);
    }

    @Test
    void deleteByIndexRemovesThatReadingFromTheNewestFirstOrder() {
        givenMeasurement(plainMeasurement("wbc"));
        Instant oldest = NOW.minusSeconds(2 * 86400);
        seedHistory("wbc", oldest, NOW.minusSeconds(86400), NOW);

        userDataService.deleteMeasurement(user, "wbc", 1);

        List<MeasurementSet> stored = user.getMeasurementHistory().get("wbc");
        assertEquals(2, stored.size());
        // The middle reading (yesterday) is gone; oldest and newest remain.
        assertTrue(stored.stream().anyMatch(s -> s.getTimestamp().equals(oldest)));
        assertTrue(stored.stream().anyMatch(s -> s.getTimestamp().equals(NOW)));
    }

    @Test
    void deleteWithInvalidIndexReturns404() {
        givenMeasurement(plainMeasurement("wbc"));
        seedHistory("wbc", NOW);

        assertThrows404(() -> userDataService.deleteMeasurement(user, "wbc", -1));
        assertThrows404(() -> userDataService.deleteMeasurement(user, "wbc", 1));
    }

    @Test
    void deleteWithUnknownMeasurementReturns404() {
        when(dataRetrievalService.getMeasurement("nope")).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown measurement: nope"));

        assertThrows404(() -> userDataService.deleteMeasurement(user, "nope", null));
    }

    @Test
    void deleteWithoutReadingsReturns404() {
        givenMeasurement(plainMeasurement("wbc"));

        assertThrows404(() -> userDataService.deleteMeasurement(user, "wbc", null));
    }

    @Test
    void deleteRecomputesLastTakenFromTheRemainingHistory() {
        givenMeasurement(plainMeasurement("wbc"));
        Instant older = NOW.minusSeconds(86400);
        seedHistory("wbc", older, NOW);
        user.getMeasurementLastTaken().put("wbc", NOW);

        userDataService.deleteMeasurement(user, "wbc", null);

        assertEquals(older, user.lastTakenOf("wbc"));
    }

    @Test
    void deleteLastReadingRemovesLastTaken() {
        givenMeasurement(plainMeasurement("wbc"));
        seedHistory("wbc", NOW);
        user.getMeasurementLastTaken().put("wbc", NOW);

        userDataService.deleteMeasurement(user, "wbc", null);

        assertNull(user.lastTakenOf("wbc"));
        assertNull(user.getMeasurementHistory().get("wbc"));
    }

    @Test
    void deletingTheNewestOfABaselineRelativeMeasurementIsAllowed() {
        givenMeasurement(percentMeasurement("body_mass"));
        seedHistory("body_mass", NOW.minusSeconds(86400), NOW);
        user.recordBaselineIfAbsent("body_mass", 70.0);

        userDataService.deleteMeasurement(user, "body_mass", 0);

        assertEquals(1, user.getMeasurementHistory().get("body_mass").size());
        // The baseline survives: it was recorded from the first reading.
        assertEquals(70.0, user.baselineOf("body_mass"));
    }

    @Test
    void deleteBaselineReadingRejectedWhileOtherReadingsExist() {
        givenMeasurement(percentMeasurement("body_mass"));
        seedHistory("body_mass", NOW.minusSeconds(86400), NOW);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> userDataService.deleteMeasurement(user, "body_mass", 1));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        // Nothing was deleted.
        assertEquals(2, user.getMeasurementHistory().get("body_mass").size());
    }

    @Test
    void deleteSoleBaselineReadingAlsoClearsTheBaseline() {
        givenMeasurement(percentMeasurement("body_mass"));
        seedHistory("body_mass", NOW);
        user.recordBaselineIfAbsent("body_mass", 70.0);
        user.getMeasurementLastTaken().put("body_mass", NOW);

        userDataService.deleteMeasurement(user, "body_mass", null);

        assertNull(user.getMeasurementHistory().get("body_mass"));
        assertNull(user.baselineOf("body_mass"));
        assertNull(user.lastTakenOf("body_mass"));
        verify(userData).save(user);
    }

    private void seedHistory(String measurementId, Instant... times) {
        List<MeasurementSet> list = user.getMeasurementHistory()
                .computeIfAbsent(measurementId, id -> new java.util.ArrayList<>());
        for (Instant time : times) {
            list.add(new MeasurementSet(measurementId, "user", time, List.of(1.0), null));
        }
    }

    private void assertThrows404(Runnable action) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void trendIsUpWhenLatestReadingIsHigher() {
        givenMeasurement(plainMeasurement("wbc"));
        userDataService.logMeasurement(user, "wbc", new LogMeasurementRequest(List.of(5000.0), null));
        userDataService.logMeasurement(user, "wbc", new LogMeasurementRequest(List.of(7000.0), null));

        MeasurementTrend trend = userDataService.getTrend(user, "wbc");

        assertEquals(TrendDirection.UP, trend.direction());
    }

    @Test
    void trendIsDownWhenLatestReadingIsLower() {
        givenMeasurement(percentMeasurement("body_mass"));
        userDataService.logMeasurement(user, "body_mass", new LogMeasurementRequest(List.of(70.0), null));
        userDataService.logMeasurement(user, "body_mass", new LogMeasurementRequest(List.of(66.0), null));

        // Compared in percent-change space: 0% then -5.7% -> down.
        assertEquals(TrendDirection.DOWN, userDataService.getTrend(user, "body_mass").direction());
    }

    @Test
    void smallChangesCountAsFlat() {
        givenMeasurement(plainMeasurement("wbc"));
        userDataService.logMeasurement(user, "wbc", new LogMeasurementRequest(List.of(7000.0), null));
        userDataService.logMeasurement(user, "wbc", new LogMeasurementRequest(List.of(7010.0), null));

        assertEquals(TrendDirection.FLAT, userDataService.getTrend(user, "wbc").direction());
    }

    @Test
    void trendHasNoDirectionWithFewerThanTwoReadings() {
        givenMeasurement(plainMeasurement("wbc"));

        MeasurementTrend trend = userDataService.getTrend(user, "wbc");

        assertNull(trend.direction());
    }
}
