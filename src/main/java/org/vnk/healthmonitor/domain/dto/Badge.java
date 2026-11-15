package org.vnk.healthmonitor.domain.dto;

import java.util.List;

/**
 * Static definition of a badge from badges.json. A badge is earned when
 * every one of its criteria is satisfied.
 */
public record Badge(
        String title,
        List<Criterion> criteria
) {
}
