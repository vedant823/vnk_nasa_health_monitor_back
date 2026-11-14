package org.vnk.healthmonitor.service;

import org.vnk.healthmonitor.domain.dto.ReminderDigest;

/**
 * Delivers a reminder digest to a user. Implementations decide the
 * channel: logging for development, push or email later.
 */
public interface NotificationSender {

    /**
     * Sends one user's daily/weekly/monthly digest.
     */
    void send(ReminderDigest digest);
}
