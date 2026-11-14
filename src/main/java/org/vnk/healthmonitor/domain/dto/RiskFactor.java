package org.vnk.healthmonitor.domain.dto;

import java.util.List;

/**
 * Static definition of a risk factor, merged from riskInfo.json.
 * {@code measurementIds} may be empty for risks that have no measurements yet.
 */
public record RiskFactor(
        String id,
        String name,
        RiskCategory category,
        List<String> measurementIds,
        String reason
) {
}
