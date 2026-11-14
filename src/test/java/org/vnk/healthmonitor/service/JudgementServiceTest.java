package org.vnk.healthmonitor.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vnk.healthmonitor.domain.dto.Instruction;
import org.vnk.healthmonitor.domain.dto.Measurement;
import org.vnk.healthmonitor.domain.dto.MeasurementCategory;
import org.vnk.healthmonitor.domain.dto.RiskEvaluation;
import org.vnk.healthmonitor.domain.dto.RiskFactor;
import org.vnk.healthmonitor.domain.document.MeasurementSet;
import org.vnk.healthmonitor.domain.document.UserDocument;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the rule-based scoring: band boundaries, penalty
 * stacking, advice ordering, and visual-measurement exclusion.
 */
class JudgementServiceTest {

    private DataRetrievalService dataRetrievalService;
    private JudgementService judgementService;
    private UserDocument user;

    @BeforeEach
    void setUp() {
        dataRetrievalService = mock(DataRetrievalService.class);
        judgementService = new JudgementService(dataRetrievalService);
        user = new UserDocument("user", "hash", "token");
    }

    private Measurement numerical(String id, Double dangerousLow, Double abnormalLow,
                                  Double abnormalHigh, Double dangerousHigh,
                                  String lowRecommendation, String highRecommendation) {
        return new Measurement(id, id, MeasurementCategory.NUMERICAL, "units",
                dangerousLow, abnormalLow, abnormalHigh, dangerousHigh, false,
                lowRecommendation, highRecommendation, List.of());
    }

    private void logValue(String measurementId, double value) {
        user.getMeasurementHistory().computeIfAbsent(measurementId, id -> new ArrayList<>())
                .add(new MeasurementSet(measurementId, "user", Instant.now(),
                        List.of(value), null));
    }

    private void givenMeasurements(Measurement... measurements) {
        for (Measurement measurement : measurements) {
            when(dataRetrievalService.getMeasurement(measurement.id())).thenReturn(measurement);
        }
    }

    @Test
    void allMeasurementsNormalScores100() {
        givenMeasurements(numerical("m1", 3000.0, 4500.0, 11000.0, 30000.0, null, null));
        logValue("m1", 7000);
        RiskFactor risk = new RiskFactor("r1", "Risk", null, List.of("m1"), "reason");

        RiskEvaluation evaluation = judgementService.evaluate(user, risk);

        assertEquals(100, evaluation.score());
        assertEquals(List.of("No action needed - all measurements are within the normal range."),
                evaluation.advice());
        assertEquals("All measurements are within the normal range.", evaluation.reason());
    }

    @Test
    void valueExactlyAtBoundIsNormal() {
        // Classification uses strict inequality, so the bound itself is normal.
        givenMeasurements(numerical("m1", 3000.0, 4500.0, 11000.0, 30000.0, null, null));
        logValue("m1", 4500);
        RiskFactor risk = new RiskFactor("r1", "Risk", null, List.of("m1"), "reason");

        assertEquals(100, judgementService.evaluate(user, risk).score());
    }

    @Test
    void justBelowAbnormalLowSubtracts40() {
        givenMeasurements(numerical("m1", 3000.0, 4500.0, 11000.0, 30000.0, null, null));
        logValue("m1", 4499);
        RiskFactor risk = new RiskFactor("r1", "Risk", null, List.of("m1"), "reason");

        RiskEvaluation evaluation = judgementService.evaluate(user, risk);
        assertEquals(60, evaluation.score());
        assertEquals(1, evaluation.advice().size());
        assertEquals("Measurement is out of the low normal range: recheck the measurement and alert the Crew Medical Officer if it repeats.",
                evaluation.advice().get(0));
    }

    @Test
    void lowRecommendationIsUsedForLowSide() {
        givenMeasurements(numerical("m1", 3000.0, 4500.0, 11000.0, 30000.0, "low rec", "high rec"));
        logValue("m1", 4499);
        RiskFactor risk = new RiskFactor("r1", "Risk", null, List.of("m1"), "reason");

        assertEquals(List.of("low rec"), judgementService.evaluate(user, risk).advice());
    }

    @Test
    void valueExactlyAtDangerousLowIsStillAbnormalNotDangerous() {
        givenMeasurements(numerical("m1", 3000.0, 4500.0, 11000.0, 30000.0, null, null));
        logValue("m1", 3000);
        RiskFactor risk = new RiskFactor("r1", "Risk", null, List.of("m1"), "reason");

        assertEquals(60, judgementService.evaluate(user, risk).score());
    }

    @Test
    void belowDangerousLowSubtracts75() {
        givenMeasurements(numerical("m1", 3000.0, 4500.0, 11000.0, 30000.0, null, null));
        logValue("m1", 2999);
        RiskFactor risk = new RiskFactor("r1", "Risk", null, List.of("m1"), "reason");

        assertEquals(25, judgementService.evaluate(user, risk).score());
    }

    @Test
    void aboveDangerousHighSubtracts75AndUsesHighAdvice() {
        givenMeasurements(numerical("m1", 3000.0, 4500.0, 11000.0, 30000.0, "low rec", "high rec"));
        logValue("m1", 30001);
        RiskFactor risk = new RiskFactor("r1", "Risk", null, List.of("m1"), "reason");

        RiskEvaluation evaluation = judgementService.evaluate(user, risk);
        assertEquals(25, evaluation.score());
        assertEquals(List.of("high rec"), evaluation.advice());
    }

    @Test
    void justAboveAbnormalHighSubtracts40() {
        givenMeasurements(numerical("m1", 3000.0, 4500.0, 11000.0, 30000.0, null, null));
        logValue("m1", 11001);
        RiskFactor risk = new RiskFactor("r1", "Risk", null, List.of("m1"), "reason");

        assertEquals(60, judgementService.evaluate(user, risk).score());
    }

    @Test
    void penaltiesStackAndFloorAtOne() {
        // Three abnormal measurements: 100 - 3*40 = -20 -> floored at 1.
        givenMeasurements(
                numerical("m1", 3000.0, 4500.0, 11000.0, 30000.0, null, null),
                numerical("m2", null, 10.0, 20.0, null, null, null),
                numerical("m3", null, 10.0, 20.0, null, null, null));
        logValue("m1", 12000);
        logValue("m2", 25);
        logValue("m3", 5);
        RiskFactor risk = new RiskFactor("r1", "Risk", null, List.of("m1", "m2", "m3"), "reason");

        assertEquals(1, judgementService.evaluate(user, risk).score());
    }

    @Test
    void dangerousPlusAbnormalStacksToFlooredScore() {
        // 100 - 75 - 40 = -15 -> floored at 1.
        givenMeasurements(
                numerical("m1", 3000.0, 4500.0, 11000.0, 30000.0, null, null),
                numerical("m2", null, 10.0, 20.0, null, null, null));
        logValue("m1", 2999);
        logValue("m2", 25);
        RiskFactor risk = new RiskFactor("r1", "Risk", null, List.of("m1", "m2"), "reason");

        assertEquals(1, judgementService.evaluate(user, risk).score());
    }

    @Test
    void adviceIsOrderedDangerousFirst() {
        givenMeasurements(
                numerical("abnormal", null, 10.0, 20.0, null, null, "abnormal advice"),
                numerical("dangerous", 5.0, 10.0, 20.0, null, "dangerous advice", null));
        logValue("abnormal", 25);
        logValue("dangerous", 1);
        RiskFactor risk = new RiskFactor("r1", "Risk", null, List.of("abnormal", "dangerous"), "reason");

        assertEquals(List.of("dangerous advice", "abnormal advice"),
                judgementService.evaluate(user, risk).advice());
    }

    @Test
    void reasonListsViolationsInSameOrder() {
        givenMeasurements(
                numerical("abnormal", null, 10.0, 20.0, null, null, null),
                numerical("dangerous", 5.0, 10.0, 20.0, null, null, null));
        logValue("abnormal", 25);
        logValue("dangerous", 1);
        RiskFactor risk = new RiskFactor("r1", "Risk", null, List.of("abnormal", "dangerous"), "reason");

        String reason = judgementService.evaluate(user, risk).reason();
        assertTrue(reason.contains("dangerous"), "dangerous violation should come first: " + reason);
        assertTrue(reason.indexOf("dangerous") < reason.indexOf("abnormal"));
    }

    @Test
    void visualMeasurementsAreExcludedFromScoringButNoted() {
        Measurement visual = new Measurement("v1", "v1", MeasurementCategory.VISUAL, null,
                null, null, null, null, false, null, null, List.of());
        givenMeasurements(visual);
        user.getMeasurementHistory().computeIfAbsent("v1", id -> new ArrayList<>())
                .add(new MeasurementSet("v1", "user", Instant.now(), null, List.of("image")));
        RiskFactor risk = new RiskFactor("r1", "Risk", null, List.of("v1"), "reason");

        RiskEvaluation evaluation = judgementService.evaluate(user, risk);
        assertEquals(100, evaluation.score());
        assertTrue(evaluation.reason().contains("Visual measurements are not yet scored"));
    }

    @Test
    void measurementsWithoutHistoryAreSkipped() {
        givenMeasurements(numerical("m1", 3000.0, 4500.0, 11000.0, 30000.0, null, null));
        RiskFactor risk = new RiskFactor("r1", "Risk", null, List.of("m1"), "reason");

        assertEquals(100, judgementService.evaluate(user, risk).score());
    }

    @Test
    void missingBoundSideDoesNotApply() {
        // m3 has only an abnormalHigh: a very low value must stay normal.
        givenMeasurements(numerical("m3", null, null, 100.0, null, null, null));
        logValue("m3", -50);
        RiskFactor risk = new RiskFactor("r1", "Risk", null, List.of("m3"), "reason");

        assertEquals(100, judgementService.evaluate(user, risk).score());
    }

    @Test
    void latestMeasurementSetIsUsedNotOlderOnes() {
        givenMeasurements(numerical("m1", 3000.0, 4500.0, 11000.0, 30000.0, null, null));
        // Old abnormal reading, then a normal one that must win.
        user.getMeasurementHistory().computeIfAbsent("m1", id -> new ArrayList<>())
                .add(new MeasurementSet("m1", "user", Instant.parse("2026-01-01T00:00:00Z"), List.of(20000.0), null));
        user.getMeasurementHistory().computeIfAbsent("m1", id -> new ArrayList<>())
                .add(new MeasurementSet("m1", "user", Instant.parse("2026-01-02T00:00:00Z"), List.of(7000.0), null));
        RiskFactor risk = new RiskFactor("r1", "Risk", null, List.of("m1"), "reason");

        assertEquals(100, judgementService.evaluate(user, risk).score());
    }

    @Test
    void valuesWithinOneSetAreAveraged() {
        givenMeasurements(numerical("m1", 3000.0, 4500.0, 11000.0, 30000.0, null, null));
        // Average of 12000 and 8000 is 10000 -> normal.
        user.getMeasurementHistory().computeIfAbsent("m1", id -> new ArrayList<>())
                .add(new MeasurementSet("m1", "user", Instant.now(), List.of(12000.0, 8000.0), null));
        RiskFactor risk = new RiskFactor("r1", "Risk", null, List.of("m1"), "reason");

        assertEquals(100, judgementService.evaluate(user, risk).score());
    }

    @Test
    void evaluateAllScoresEveryRisk() {
        givenMeasurements(numerical("m1", 3000.0, 4500.0, 11000.0, 30000.0, null, null));
        logValue("m1", 12000);
        when(dataRetrievalService.getRisks()).thenReturn(List.of(
                new RiskFactor("r1", "Risk One", null, List.of("m1"), "reason"),
                new RiskFactor("r2", "Risk Two", null, List.of(), "reason")));

        List<RiskEvaluation> evaluations = judgementService.evaluateAll(user);

        assertEquals(2, evaluations.size());
        assertEquals("r1", evaluations.get(0).riskId());
        assertEquals(60, evaluations.get(0).score());
        assertEquals("r2", evaluations.get(1).riskId());
        assertEquals(100, evaluations.get(1).score());
    }
}
