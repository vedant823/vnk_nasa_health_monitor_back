package org.vnk.healthmonitor.domain.dto;

import java.util.List;

/**
 * Static definition of a measurement, merged from measurementDetails.json
 * (thresholds, recommendations) and measurementInstructions.json (steps).
 *
 * Thresholds are optional: a null bound means that side of the range does
 * not apply (e.g. {@code mood_score} has no dangerousHigh).
 */
public record Measurement(
        String id,
        String name,
        MeasurementCategory category,
        String unit,
        Double dangerousLow,
        Double abnormalLow,
        Double abnormalHigh,
        Double dangerousHigh,
        boolean canBeNegative,
        BaselineMode baselineMode,
        String lowRecommendation,
        String highRecommendation,
        List<Instruction> instructions
) {
}
