package org.vnk.healthmonitor.domain.dto;

import java.util.List;

/**
 * A newly logged measurement value. Exactly one of {@code values}
 * (numerical measurements) or {@code image} (visual measurements,
 * base64-encoded) must be present.
 */
public record LogMeasurementRequest(
        List<Double> values,
        String image
) {
}
