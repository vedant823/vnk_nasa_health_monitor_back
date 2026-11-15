package org.vnk.healthmonitor.service;

import org.springframework.stereotype.Service;
import org.vnk.healthmonitor.domain.dto.Measurement;
import org.vnk.healthmonitor.domain.dto.Environment;
import org.vnk.healthmonitor.domain.dto.MeasurementCategory;
import org.vnk.healthmonitor.domain.dto.Range;
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
 * the advice, most severe first. Visual measurements are judged by the
 * number of abnormalities the Crew Medical Officer recorded via
 * PUT /users/measurements/{id}/score: one counts as abnormal, two or
 * more as dangerous, and an unreviewed image (no count yet) is noted in
 * the reason without a penalty. Measurements expressed relative to a
 * baseline are converted from raw readings to their change from the
 * user's baseline first.
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
     * Evaluates one risk factor for the user, classifying each latest
     * reading against the guideline ranges for the user's environment.
     */
    public RiskEvaluation evaluate(UserDocument user, RiskFactor risk) {
        Environment environment = user.environmentOf();
        List<Anomaly> dangerous = new ArrayList<>();
        List<Anomaly> abnormal = new ArrayList<>();
        boolean hasVisual = false;

        for (String measurementId : risk.measurementIds()) {
            Measurement measurement = dataRetrievalService.getMeasurement(measurementId);
            if (measurement.category() == MeasurementCategory.VISUAL) {
                List<MeasurementSet> visualHistory = user.historyOf(measurementId);
                if (visualHistory.isEmpty()) {
                    continue;
                }
                Integer numAbnormal = visualHistory.get(visualHistory.size() - 1).getNumAbnormal();
                if (numAbnormal == null) {
                    // Logged but not yet reviewed by the Crew Medical Officer.
                    hasVisual = true;
                } else if (numAbnormal == 1) {
                    abnormal.add(describeVisual(measurement, numAbnormal));
                } else if (numAbnormal > 1) {
                    dangerous.add(describeVisual(measurement, numAbnormal));
                }
                continue;
            }
            List<MeasurementSet> history = user.historyOf(measurementId);
            if (history.isEmpty()) {
                continue;
            }
            MeasurementSet latest = history.get(history.size() - 1);
            double raw = average(latest.getValues());
            Double change = user.changeFromBaseline(measurementId, raw, measurement.baselineMode());
            double value = change != null ? change : raw;
            Band band = classify(value, measurement.rangeFor(environment));
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

    private Band classify(double value, Range range) {
        if (range.dangerousLow() != null && value < range.dangerousLow()) {
            return Band.DANGEROUS_LOW;
        }
        if (range.abnormalLow() != null && value < range.abnormalLow()) {
            return Band.ABNORMAL_LOW;
        }
        if (range.dangerousHigh() != null && value > range.dangerousHigh()) {
            return Band.DANGEROUS_HIGH;
        }
        if (range.abnormalHigh() != null && value > range.abnormalHigh()) {
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

    /**
     * A visual measurement's abnormality count maps onto the same
     * penalty scale as the numerical bands: one observed abnormality is
     * abnormal (-40), two or more are dangerous (-75). Zero is normal.
     */
    private Anomaly describeVisual(Measurement measurement, int numAbnormal) {
        String description = measurement.name() + " shows " + numAbnormal
                + (numAbnormal == 1 ? " abnormality" : " abnormalities");
        String recommendation = measurement.name()
                + " shows abnormalities: review the flagged findings with the Crew Medical Officer.";
        int penalty = numAbnormal == 1 ? ABNORMAL_PENALTY : DANGEROUS_PENALTY;
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
