package org.vnk.healthmonitor.domain.dto;

import java.util.List;

/**
 * One condition of a badge. A reading of any of the listed measurement
 * IDs qualifies; {@code distinctDays} requires one qualifying reading per
 * day, {@code streak} requires the qualifying days to be consecutive
 * (which implies distinct days), and {@code isOk} requires every
 * qualifying reading to be within the measurement's normal range.
 * {@code count} is how many qualifying readings (or days) are needed.
 */
public record Criterion(
        List<String> measurementType,
        boolean distinctDays,
        boolean streak,
        boolean isOk,
        int count
) {
}
