package org.vnk.healthmonitor.domain.dto;

import java.util.List;

/**
 * A badge the user has not earned yet, with the fraction (0.0-1.0) of
 * measurements completed in each of its criteria.
 */
public record BadgeStatus(
        Badge badge,
        List<Double> progress
) {
}
