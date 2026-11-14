package org.vnk.healthmonitor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.vnk.healthmonitor.domain.dto.Reminder;
import org.vnk.healthmonitor.domain.dto.ReminderDigest;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Development implementation of {@link NotificationSender} that logs the
 * digest. Push or email implementations plug in behind the same interface
 * without touching the scheduler's logic.
 */
@Component
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_INSTANT;

    @Override
    public void send(ReminderDigest digest) {
        log.info("Reminder digest for {}: {} daily [{}], {} weekly [{}], {} monthly [{}]",
                digest.username(),
                digest.daily().size(), ids(digest.daily()),
                digest.weekly().size(), ids(digest.weekly()),
                digest.monthly().size(), ids(digest.monthly()));
    }

    private String ids(java.util.List<Reminder> reminders) {
        StringBuilder builder = new StringBuilder();
        for (Reminder reminder : reminders) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(reminder.measurementId())
                    .append('@')
                    .append(FORMAT.format(reminder.dueDate().atZone(ZoneOffset.UTC)));
        }
        return builder.toString();
    }
}
