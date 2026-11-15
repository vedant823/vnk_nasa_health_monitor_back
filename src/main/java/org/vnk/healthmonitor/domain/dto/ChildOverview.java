package org.vnk.healthmonitor.domain.dto;

import java.time.Instant;

/**
 * One of an officer's children, as listed by GET /users/children:
 * enough for the officer's home overview without pulling full histories.
 */
public record ChildOverview(
        String userId,
        String username,
        Instant latestMeasurement) {
}
