package org.vnk.healthmonitor.domain.dto;

import java.util.List;

/**
 * Computed evaluation of one risk factor for a user.
 * Score starts at 100 and is lowered by abnormal measurements (floor of 1).
 * {@code advice} collects the recommendation string of every abnormal
 * measurement, most severe first.
 */
public record RiskEvaluation(
        String riskId,
        int score,
        List<String> advice,
        String reason
) {
}
