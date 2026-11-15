package org.vnk.healthmonitor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.vnk.healthmonitor.domain.dto.Reminder;
import org.vnk.healthmonitor.domain.dto.ReminderDigest;
import org.vnk.healthmonitor.domain.document.UserDocument;
import org.vnk.healthmonitor.repository.UserData;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs daily and emits every user's reminder digest for the next day,
 * week, and month. The buckets are built by partitioning a single
 * snapshot of {@link UserReminderService#getUpcoming} over 30 days, so
 * the due-date math lives in exactly one place.
 *
 * The buckets are disjoint: each measurement lands in exactly one bucket,
 * the smallest window it is due within (overdue measurements sit in
 * daily). {@code app.notifications.enabled} gates only the scheduled
 * sending; the bean always exists so the digest stays servable over HTTP
 * (GET /users/digest).
 */
@Component
public class NotificationScheduler {

    static final int DAILY_WINDOW_DAYS = 1;
    static final int WEEKLY_WINDOW_DAYS = 7;
    static final int MONTHLY_WINDOW_DAYS = 30;

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);
    private final UserData userData;
    private final UserReminderService userReminderService;
    private final NotificationSender sender;
    private final Clock clock;
    private final boolean notificationsEnabled;

    public NotificationScheduler(UserData userData,
                                 UserReminderService userReminderService,
                                 NotificationSender sender,
                                 Clock clock,
                                 @org.springframework.beans.factory.annotation.Value("${app.notifications.enabled:true}") boolean notificationsEnabled) {
        this.userData = userData;
        this.userReminderService = userReminderService;
        this.sender = sender;
        this.clock = clock;
        this.notificationsEnabled = notificationsEnabled;
    }

    /**
     * Builds one user's digest from a single snapshot of the month
     * window, partitioned into disjoint buckets: daily (due within a
     * day, including overdue), weekly (the rest of the week), and
     * monthly (the rest of the month). Plain and Clock-driven, so it is
     * unit-testable without waiting for the schedule.
     */
    public ReminderDigest buildDigest(UserDocument user) {
        List<Reminder> month = userReminderService.getUpcoming(user, MONTHLY_WINDOW_DAYS);
        Instant dailyHorizon = clock.instant().plus(Duration.ofDays(DAILY_WINDOW_DAYS));
        Instant weeklyHorizon = clock.instant().plus(Duration.ofDays(WEEKLY_WINDOW_DAYS));
        List<Reminder> daily = new ArrayList<>();
        List<Reminder> weekly = new ArrayList<>();
        List<Reminder> monthly = new ArrayList<>();
        // The snapshot is ordered by due date, so each bucket stays ordered.
        for (Reminder reminder : month) {
            Instant due = reminder.dueDate();
            if (!due.isAfter(dailyHorizon)) {
                daily.add(reminder);
            } else if (!due.isAfter(weeklyHorizon)) {
                weekly.add(reminder);
            } else {
                monthly.add(reminder);
            }
        }
        return new ReminderDigest(user.getUsername(),
                List.copyOf(daily), List.copyOf(weekly), List.copyOf(monthly));
    }

    /**
     * Sends every user their digest when notifications are enabled. A
     * failure sending to one user must not stop the others.
     */
    @Scheduled(cron = "${app.notifications.cron}")
    public void sendDailyDigests() {
        if (!notificationsEnabled) {
            return;
        }
        for (UserDocument user : userData.findAll()) {
            try {
                sender.send(buildDigest(user));
            } catch (RuntimeException e) {
                log.warn("Failed to send reminder digest to user {}", user.getUsername(), e);
            }
        }
    }
}
