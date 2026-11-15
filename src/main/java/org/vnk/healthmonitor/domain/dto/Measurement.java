package org.vnk.healthmonitor.domain.dto;

import java.util.List;

/**
 * Static definition of a measurement, merged from measurementDetails.json
 * (per-environment thresholds, recommendations) and
 * measurementInstructions.json (steps).
 *
 * Each environment has its own {@link Range}; a null bound inside a range
 * means that side does not apply there.
 */
public record Measurement(
        String id,
        String name,
        MeasurementCategory category,
        String unit,
        List<Range> ranges,
        boolean canBeNegative,
        BaselineMode baselineMode,
        String lowRecommendation,
        String highRecommendation,
        List<Instruction> instructions
) {

    /**
     * The guideline bounds for one environment. Loading validates that
     * every measurement covers every environment, so this only fails on
     * a programming error.
     */
    public Range rangeFor(Environment environment) {
        for (Range range : ranges) {
            if (range.situation() == environment) {
                return range;
            }
        }
        throw new IllegalStateException(
                "Measurement " + id + " has no range for environment " + environment.getLabel());
    }
}
