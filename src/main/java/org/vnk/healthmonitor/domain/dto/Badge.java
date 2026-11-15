package org.vnk.healthmonitor.domain.dto;

import java.util.List;

/**
 * Static definition of a badge from badges.json. A badge is earned when
 * every one of its criteria is satisfied; the description is the
 * human-readable summary of what to do to earn it.
 */
public record Badge(
        String title,
        String description,
        List<Criterion> criteria
) {
}
