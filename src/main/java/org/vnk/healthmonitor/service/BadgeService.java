package org.vnk.healthmonitor.service;

import org.springframework.stereotype.Service;
import org.vnk.healthmonitor.domain.dto.Badge;
import org.vnk.healthmonitor.domain.dto.BadgeSet;
import org.vnk.healthmonitor.domain.dto.BadgeStatus;
import org.vnk.healthmonitor.domain.dto.Criterion;
import org.vnk.healthmonitor.domain.dto.Measurement;
import org.vnk.healthmonitor.domain.dto.MeasurementCategory;
import org.vnk.healthmonitor.domain.document.MeasurementSet;
import org.vnk.healthmonitor.domain.document.UserDocument;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Evaluates the user's badges against their measurement history. A badge
 * is earned when every one of its criteria is satisfied; a criterion is
 * satisfied by enough qualifying readings, where isOk requires the
 * reading to be within the measurement's normal range (baseline-adjusted
 * like the risk scoring), distinctDays counts each day once, and streak
 * requires consecutive qualifying days. Visual measurements never
 * qualify for isOk criteria, since they have no normal range.
 *
 * Evaluation is live from history: no badge state is stored, so the
 * response always reflects the user's current readings.
 */
@Service
public class BadgeService {

    private final DataRetrievalService dataRetrievalService;

    public BadgeService(DataRetrievalService dataRetrievalService) {
        this.dataRetrievalService = dataRetrievalService;
    }

    /**
     * Evaluates every badge for the user, splitting them into earned
     * badges and unearned ones with per-criterion progress fractions.
     */
    public BadgeSet evaluate(UserDocument user) {
        List<Badge> earned = new ArrayList<>();
        List<BadgeStatus> unearned = new ArrayList<>();
        for (Badge badge : dataRetrievalService.getBadges()) {
            List<Double> progress = new ArrayList<>();
            boolean allMet = true;
            for (Criterion criterion : badge.criteria()) {
                double fraction = progressOf(user, criterion);
                progress.add(fraction);
                if (fraction < 1.0) {
                    allMet = false;
                }
            }
            if (allMet) {
                earned.add(badge);
            } else {
                unearned.add(new BadgeStatus(badge, List.copyOf(progress)));
            }
        }
        return new BadgeSet(List.copyOf(earned), List.copyOf(unearned));
    }

    /**
     * The fraction (0.0-1.0) of a criterion's required readings (or
     * days) the user has completed. For streaks this is the best run of
     * consecutive qualifying days over the required length, since that
     * is what stands between the user and the badge.
     */
    private double progressOf(UserDocument user, Criterion criterion) {
        if (criterion.streak()) {
            SortedSet<Long> days = qualifyingDays(user, criterion);
            return fractionOf(bestRunLength(days), criterion.count());
        }
        if (criterion.distinctDays()) {
            return fractionOf(qualifyingDays(user, criterion).size(), criterion.count());
        }
        return fractionOf(qualifyingReadings(user, criterion).size(), criterion.count());
    }

    private double fractionOf(int achieved, int required) {
        return Math.min(1.0, (double) achieved / required);
    }

    /**
     * Every qualifying reading's timestamp: readings of any of the
     * criterion's measurement types, green when isOk is set.
     */
    private List<Instant> qualifyingReadings(UserDocument user, Criterion criterion) {
        List<Instant> timestamps = new ArrayList<>();
        for (String measurementId : criterion.measurementType()) {
            Measurement measurement = dataRetrievalService.getMeasurement(measurementId);
            for (MeasurementSet set : user.historyOf(measurementId)) {
                if (criterion.isOk() && !isGreen(user, measurementId, set, measurement)) {
                    continue;
                }
                timestamps.add(set.getTimestamp());
            }
        }
        return timestamps;
    }

    /**
     * The UTC calendar days of every qualifying reading, each day counted
     * once no matter how many qualifying readings it holds.
     */
    private SortedSet<Long> qualifyingDays(UserDocument user, Criterion criterion) {
        SortedSet<Long> days = new TreeSet<>();
        for (Instant timestamp : qualifyingReadings(user, criterion)) {
            days.add(utcDay(timestamp));
        }
        return days;
    }

    /**
     * The longest run of consecutive UTC days in the set.
     */
    private int bestRunLength(SortedSet<Long> days) {
        int best = 0;
        int current = 0;
        long previous = Long.MIN_VALUE;
        for (long day : days) {
            current = (day == previous + 1) ? current + 1 : 1;
            best = Math.max(best, current);
            previous = day;
        }
        return best;
    }

    /**
     * Whether one reading is within the measurement's normal range,
     * baseline-adjusted the same way the risk scoring and the history
     * endpoint convert values. Visual measurements have no ranges, so
     * they are never green.
     */
    private boolean isGreen(UserDocument user, String measurementId,
                            MeasurementSet set, Measurement measurement) {
        if (measurement.category() == MeasurementCategory.VISUAL) {
            return false;
        }
        double raw = average(set.getValues());
        Double change = user.changeFromBaseline(measurementId, raw, measurement.baselineMode());
        double value = change != null ? change : raw;
        return isWithinNormalRange(value, measurement);
    }

    private boolean isWithinNormalRange(double value, Measurement measurement) {
        if (measurement.dangerousLow() != null && value < measurement.dangerousLow()) {
            return false;
        }
        if (measurement.abnormalLow() != null && value < measurement.abnormalLow()) {
            return false;
        }
        if (measurement.abnormalHigh() != null && value > measurement.abnormalHigh()) {
            return false;
        }
        if (measurement.dangerousHigh() != null && value > measurement.dangerousHigh()) {
            return false;
        }
        return true;
    }

    private long utcDay(Instant timestamp) {
        return timestamp.atZone(ZoneOffset.UTC).toLocalDate().toEpochDay();
    }

    private double average(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }
}
