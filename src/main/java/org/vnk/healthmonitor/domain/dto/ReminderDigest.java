package org.vnk.healthmonitor.domain.dto;

import java.util.List;

/**
 * The daily/weekly/monthly reminder digest computed for one user by the
 * notification scheduler. Each bucket contains every measurement due
 * within that window (so buckets nest: daily is included in weekly and
 * monthly); the sender decides how to present them.
 */
public record ReminderDigest(
        String username,
        List<Reminder> daily,
        List<Reminder> weekly,
        List<Reminder> monthly
) {
}
