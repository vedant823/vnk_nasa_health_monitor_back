package org.vnk.healthmonitor.service;

import org.springframework.stereotype.Service;
import org.vnk.healthmonitor.domain.dto.Measurement;
import org.vnk.healthmonitor.domain.dto.RiskEvaluation;
import org.vnk.healthmonitor.domain.dto.RiskFactor;
import org.vnk.healthmonitor.domain.document.MeasurementSet;
import org.vnk.healthmonitor.domain.document.UserDocument;

import java.util.ArrayList;
import java.util.List;

/**
 * Judges each measurement against the guideline ranges in
 * measurementDetails.json to produce risk scores. Rule-based only:
 * no statistical modelling and no AI.
 *
 * Scoring starts at 100; each abnormal measurement lowers the score
 * (abnormal -40, dangerous -75, stacked, floored at 1). The
 * recommendation string of every abnormal measurement is collected into
 * the advice, most severe first. Visual measurements are excluded from
 * scoring.
 */
@Service
public class JudgementService {

    private enum Band { NORMAL, ABNORMAL_LOW, DANGEROUS_LOW, ABNORMAL_HIGH, DANGEROUS_HIGH }

    private record Anomaly(String description, String recommendation, int penalty) {
    }

    private static final int ABNORMAL_PENALTY = 40;
    private static final int DANGEROUS_PENALTY = 75;

    private final DataRetrievalService dataRetrievalService;

    public JudgementService(DataRetrievalService dataRetrievalService) {
        this.dataRetrievalService = dataRetrievalService;
    }

    /**
     * Evaluates one risk factor for a user.
     */
    public RiskEvaluation evaluate(UserDocument user, RiskFactor risk) {
        List<Anomaly> dangerous = new ArrayList<>();
        List<Anomaly> abnormal = new ArrayList<>();
        boolean hasVisual = false;

        for (String measurementId : risk.measurementIds()) {
            Measurement measurement = dataRetrievalService.getMeasurement(measurementId);
            if (measurement.category() == org.vnk.healthmonitor.domain.dto.MeasurementCategory.VISUAL) {
                hasVisual = true;
                continue;
            }
            List<MeasurementSet> history = user.historyOf(measurementId);
            if (history.isEmpty()) {
                continue;
            }
            double value = average(history.get(history.size() - 1).getValues());
            Band band = classify(value, measurement);
            if (band == Band.NORMAL) {
                continue;
            }
            Anomaly anomaly = describe(value, measurement, band);
            if (band == Band.DANGEROUS_LOW || band == Band.DANGEROUS_HIGH) {
                dangerous.add(anomaly);
            } else {
                abnormal.add(anomaly);
            }
        }

        int penalty = 0;
        List<String> advice = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (Anomaly anomaly : concat(dangerous, abnormal)) {
            penalty += anomaly.penalty();
            advice.add(anomaly.recommendation());
            reasons.add(anomaly.description());
        }
        if (hasVisual) {
            reasons.add("Visual measurements are not yet scored");
        }

        int score = Math.max(1, 100 - penalty);
        if (advice.isEmpty()) {
            advice.add("No action needed - all measurements are within the normal range.");
        }
        String reason = reasons.isEmpty() ? "All measurements are within the normal range." : String.join("; ", reasons);
        return new RiskEvaluation(risk.id(), score, List.copyOf(advice), reason);
    }

    /**
     * Evaluates every risk factor for a user.
     */
    public List<RiskEvaluation> evaluateAll(UserDocument user) {
        List<RiskEvaluation> evaluations = new ArrayList<>();
        for (RiskFactor risk : dataRetrievalService.getRisks()) {
            evaluations.add(evaluate(user, risk));
        }
        return evaluations;
    }

    private Band classify(double value, Measurement measurement) {
        if (measurement.dangerousLow() != null && value < measurement.dangerousLow()) {
            return Band.DANGEROUS_LOW;
        }
        if (measurement.abnormalLow() != null && value < measurement.abnormalLow()) {
            return Band.ABNORMAL_LOW;
        }
        if (measurement.dangerousHigh() != null && value > measurement.dangerousHigh()) {
            return Band.DANGEROUS_HIGH;
        }
        if (measurement.abnormalHigh() != null && value > measurement.abnormalHigh()) {
            return Band.ABNORMAL_HIGH;
        }
        return Band.NORMAL;
    }

    private Anomaly describe(double value, Measurement measurement, Band band) {
        String description;
        String recommendation;
        int penalty;
        switch (band) {
            case DANGEROUS_LOW -> {
                description = measurement.name() + " " + value + " " + measurement.unit()
                        + " is in the dangerous low range";
                recommendation = recommendationOr(measurement.lowRecommendation(), "low");
                penalty = DANGEROUS_PENALTY;
            }
            case ABNORMAL_LOW -> {
                description = measurement.name() + " " + value + " " + measurement.unit()
                        + " is below the normal range";
                recommendation = recommendationOr(measurement.lowRecommendation(), "low");
                penalty = ABNORMAL_PENALTY;
            }
            case DANGEROUS_HIGH -> {
                description = measurement.name() + " " + value + " " + measurement.unit()
                        + " is in the dangerous high range";
                recommendation = recommendationOr(measurement.highRecommendation(), "high");
                penalty = DANGEROUS_PENALTY;
            }
            default -> {
                description = measurement.name() + " " + value + " " + measurement.unit()
                        + " is above the normal range";
                recommendation = recommendationOr(measurement.highRecommendation(), "high");
                penalty = ABNORMAL_PENALTY;
            }
        }
        return new Anomaly(description, recommendation, penalty);
    }

    private String recommendationOr(String recommendation, String side) {
        if (recommendation != null && !recommendation.isBlank()) {
            return recommendation;
        }
        return "Measurement is out of the " + side
                + " normal range: recheck the measurement and alert the Crew Medical Officer if it repeats.";
    }

    private double average(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private List<Anomaly> concat(List<Anomaly> dangerous, List<Anomaly> abnormal) {
        List<Anomaly> all = new ArrayList<>(dangerous);
        all.addAll(abnormal);
        return all;
    }
}
