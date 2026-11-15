package org.vnk.healthmonitor.domain.dto;

import java.util.List;

/**
 * The user's badges: the definitions of every earned badge and, for the
 * rest, the badge plus per-criterion progress toward it.
 */
public record BadgeSet(
        List<Badge> earned,
        List<BadgeStatus> unearned
) {
}
