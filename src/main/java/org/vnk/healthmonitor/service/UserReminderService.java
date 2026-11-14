package org.vnk.healthmonitor.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.vnk.healthmonitor.domain.dto.Reminder;
import org.vnk.healthmonitor.domain.document.UserDocument;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Compiles every measurement and how soon it should be taken,
 * based on measurement times and frequencies.
 */
@Service
public class UserReminderService {

    private final DataRetrievalService dataRetrievalService;
    private final int defaultFrequencyDays;
    private final Clock clock;

    public UserReminderService(DataRetrievalService dataRetrievalService,
                               @Value("${app.defaults.measurement-frequency-days}") int defaultFrequencyDays,
                               Clock clock) {
        this.dataRetrievalService = dataRetrievalService;
        this.defaultFrequencyDays = defaultFrequencyDays;
        this.clock = clock;
    }

    /**
     * Returns upcoming measurements within the given number of days,
     * ordered by date to be taken. Never-taken measurements are due now.
     */
    public List<Reminder> getUpcoming(UserDocument user, int withinDays) {
        Instant horizon = clock.instant().plus(Duration.ofDays(withinDays));
        List<Reminder> reminders = new ArrayList<>();
        for (String measurementId : dataRetrievalService.getMeasurementIds()) {
            Integer frequency = user.frequencyOf(measurementId);
            int days = frequency != null ? frequency : defaultFrequencyDays;
            Instant lastTaken = user.lastTakenOf(measurementId);
            Instant dueDate = lastTaken != null
                    ? lastTaken.plus(Duration.ofDays(days))
                    : clock.instant();
            if (!dueDate.isAfter(horizon)) {
                reminders.add(new Reminder(measurementId, dueDate));
            }
        }
        reminders.sort(Comparator.comparing(Reminder::dueDate));
        return reminders;
    }
}
