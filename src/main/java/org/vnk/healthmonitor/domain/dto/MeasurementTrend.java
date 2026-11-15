package org.vnk.healthmonitor.domain.dto;

/**
 * The direction of a measurement's recent history, comparing its two
 * most recent readings (baseline-adjusted for measurements relative to
 * a baseline). {@code direction} is null when there is not enough
 * history to compare.
 */
public record MeasurementTrend(
        String measurementId,
        TrendDirection direction
) {
}
