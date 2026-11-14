package org.vnk.healthmonitor.domain.dto;

import java.time.Instant;

/**
 * An upcoming measurement the user needs to take.
 */
public record Reminder(
        String measurementId,
        Instant dueDate
) {
}
