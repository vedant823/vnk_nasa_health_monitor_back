package org.vnk.healthmonitor.domain.dto;

import java.util.List;

/**
 * The daily/weekly/monthly reminder digest computed for one user by the
 * notification scheduler. The buckets are disjoint: every measurement
 * due within a month appears in exactly one bucket, the smallest window
 * it falls within — daily holds the urgent (including overdue) set,
 * weekly the rest of the week, monthly the rest of the month.
 */
public record ReminderDigest(
        String username,
        List<Reminder> daily,
        List<Reminder> weekly,
        List<Reminder> monthly
) {
}
