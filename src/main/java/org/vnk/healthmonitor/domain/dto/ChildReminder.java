package org.vnk.healthmonitor.domain.dto;

import java.time.Instant;

/**
 * One overdue measurement of an officer's child, for
 * GET /users/overdue. The child ID rides along because the officer's
 * children share one list — a bare Reminder could not say whose
 * measurement is overdue.
 */
public record ChildReminder(
        String childId,
        String measurementId,
        Instant dueDate) {
}
