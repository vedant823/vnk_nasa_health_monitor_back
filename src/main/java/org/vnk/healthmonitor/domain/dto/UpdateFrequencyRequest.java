package org.vnk.healthmonitor.domain.dto;

import jakarta.validation.constraints.Min;

/**
 * Updates how many days should pass between reminders for one measurement.
 */
public record UpdateFrequencyRequest(
        @Min(1) int frequencyDays
) {
}
