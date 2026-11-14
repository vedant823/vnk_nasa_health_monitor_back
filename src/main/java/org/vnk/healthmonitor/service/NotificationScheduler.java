package org.vnk.healthmonitor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.vnk.healthmonitor.domain.dto.Reminder;
import org.vnk.healthmonitor.domain.dto.ReminderDigest;
import org.vnk.healthmonitor.domain.document.UserDocument;
import org.vnk.healthmonitor.repository.UserData;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Runs daily and emits every user's reminder digest for the next day,
 * week, and month. The buckets are built by reusing
 * {@link UserReminderService#getUpcoming} with different windows, so the
 * due-date math lives in exactly one place.
 *
 * Gated by {@code app.notifications.enabled} so tests (or deployments)
 * can turn notifications off without touching code.
 */
@Component
@ConditionalOnProperty(name = "app.notifications.enabled", havingValue = "true")
public class NotificationScheduler {

    static final int DAILY_WINDOW_DAYS = 1;
    static final int WEEKLY_WINDOW_DAYS = 7;
    static final int MONTHLY_WINDOW_DAYS = 30;

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);
    private final UserData userData;
    private final UserReminderService userReminderService;
    private final NotificationSender sender;
    private final Clock clock;

    public NotificationScheduler(UserData userData,
                                 UserReminderService userReminderService,
                                 NotificationSender sender,
                                 Clock clock) {
        this.userData = userData;
        this.userReminderService = userReminderService;
        this.sender = sender;
        this.clock = clock;
    }

    /**
     * Builds one user's digest from a single snapshot of the month
     * window, so the three buckets share identical Reminder objects and
     * always nest (daily inside weekly inside monthly). Plain and
     * Clock-driven, so it is unit-testable without waiting for the
     * schedule.
     */
    public ReminderDigest buildDigest(UserDocument user) {
        List<Reminder> monthly = userReminderService.getUpcoming(user, MONTHLY_WINDOW_DAYS);
        Instant now = clock.instant();
        return new ReminderDigest(
                user.getUsername(),
                dueWithin(monthly, now, DAILY_WINDOW_DAYS),
                dueWithin(monthly, now, WEEKLY_WINDOW_DAYS),
                monthly);
    }

    private List<Reminder> dueWithin(List<Reminder> reminders, Instant now, int days) {
        Instant horizon = now.plus(Duration.ofDays(days));
        return reminders.stream()
                .filter(reminder -> !reminder.dueDate().isAfter(horizon))
                .toList();
    }

    /**
     * Sends every user their digest. A failure sending to one user must
     * not stop the others.
     */
    @Scheduled(cron = "${app.notifications.cron}")
    public void sendDailyDigests() {
        for (UserDocument user : userData.findAll()) {
            try {
                sender.send(buildDigest(user));
            } catch (RuntimeException e) {
                log.warn("Failed to send reminder digest to user {}", user.getUsername(), e);
            }
        }
    }
}
