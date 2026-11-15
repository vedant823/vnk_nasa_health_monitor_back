package org.vnk.healthmonitor.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.vnk.healthmonitor.domain.dto.BaselineMode;
import org.vnk.healthmonitor.domain.dto.Environment;
import org.vnk.healthmonitor.domain.dto.LogMeasurementRequest;
import org.vnk.healthmonitor.domain.dto.Measurement;
import org.vnk.healthmonitor.domain.dto.MeasurementCategory;
import org.vnk.healthmonitor.domain.dto.MeasurementTrend;
import org.vnk.healthmonitor.domain.dto.TrendDirection;
import org.vnk.healthmonitor.domain.document.MeasurementSet;
import org.vnk.healthmonitor.domain.document.UserDocument;
import org.vnk.healthmonitor.repository.UserData;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Stores and retrieves user data from the repository.
 * Logging a measurement also updates the measurement's last-taken time
 * and records the first reading of a baseline-relative measurement as
 * its baseline. Readings of baseline-relative measurements are stored
 * raw and converted to their change from baseline on the way out, so
 * users log plain values (e.g. body mass in kilograms) instead of
 * pre-computed percent changes.
 */
@Service
public class UserDataService {

    /** Two readings within 0.5% of each other count as a flat trend. */
    private static final double FLAT_FRACTION = 0.005;

    private final UserData userData;
    private final DataRetrievalService dataRetrievalService;
    private final ImageCompressionService imageCompressionService;
    private final Clock clock;

    public UserDataService(UserData userData, DataRetrievalService dataRetrievalService,
                           ImageCompressionService imageCompressionService, Clock clock) {
        this.userData = userData;
        this.dataRetrievalService = dataRetrievalService;
        this.imageCompressionService = imageCompressionService;
        this.clock = clock;
    }

    /**
     * Returns the user's logged measurements for one measurement ID,
     * newest first. Values of baseline-relative measurements are
     * converted to their change from the user's baseline.
     */
    public List<MeasurementSet> getMeasurements(UserDocument user, String measurementId) {
        Measurement measurement = requireMeasurement(measurementId);
        List<MeasurementSet> history = new ArrayList<>(user.historyOf(measurementId));
        history.sort(Comparator.comparing(MeasurementSet::getTimestamp).reversed());
        if (measurement.category() == MeasurementCategory.NUMERICAL
                && measurement.baselineMode() != BaselineMode.NONE) {
            history = history.stream()
                    .map(set -> withChangeFromBaseline(user, measurementId, set, measurement.baselineMode()))
                    .toList();
        }
        return history;
    }

    /**
     * Returns the direction of the two most recent readings of a
     * measurement (baseline-adjusted like the history endpoint), or a
     * null direction when there is not enough history to compare.
     */
    public MeasurementTrend getTrend(UserDocument user, String measurementId) {
        Measurement measurement = requireMeasurement(measurementId);
        List<MeasurementSet> history = new ArrayList<>(user.historyOf(measurementId));
        history.sort(Comparator.comparing(MeasurementSet::getTimestamp));
        if (history.size() < 2) {
            return new MeasurementTrend(measurementId, null);
        }
        double previous = adjustedAverage(user, measurementId, history.get(history.size() - 2), measurement);
        double latest = adjustedAverage(user, measurementId, history.get(history.size() - 1), measurement);
        return new MeasurementTrend(measurementId, directionOf(previous, latest));
    }

    /**
     * Logs a new MeasurementSet and updates the last-taken time.
     * Numerical measurements require values; visual measurements require
     * an image. Values are rejected if negative for a measurement that
     * cannot be negative.
     */
    public void logMeasurement(UserDocument user, String measurementId, LogMeasurementRequest request) {
        Measurement measurement = requireMeasurement(measurementId);

        List<Double> values = null;
        List<String> images = null;
        switch (measurement.category()) {
            case NUMERICAL -> {
                if (request.values() == null || request.values().isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Numerical measurement requires a 'values' list");
                }
                for (Double value : request.values()) {
                    if (value == null || !Double.isFinite(value)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Values must be finite numbers");
                    }
                    if (!measurement.canBeNegative() && value < 0) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "This measurement cannot be negative");
                    }
                }
                values = List.copyOf(request.values());
            }
            case VISUAL -> {
                if (request.image() == null || request.image().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Visual measurement requires an 'image'");
                }
                // The upload is compressed server-side (downscaled JPEG),
                // so a history of scans stays well under MongoDB's 16 MB
                // document limit.
                images = List.of(imageCompressionService.compress(request.image()));
            }
        }

        // The first reading of a baseline-relative measurement becomes the
        // reference every later reading is compared against.
        if (measurement.category() == MeasurementCategory.NUMERICAL
                && measurement.baselineMode() != BaselineMode.NONE) {
            user.recordBaselineIfAbsent(measurementId, average(values));
        }

        Instant now = clock.instant();
        MeasurementSet set = new MeasurementSet(measurementId, user.getId(), now, values, images);
        user.getMeasurementHistory().computeIfAbsent(measurementId, id -> new ArrayList<>()).add(set);
        user.getMeasurementLastTaken().put(measurementId, now);
        userData.save(user);
    }

    /**
     * Updates how many days pass between reminders for a measurement.
     */
    public void setFrequency(UserDocument user, String measurementId, int frequencyDays) {
        requireMeasurement(measurementId);
        user.getMeasurementFrequencies().put(measurementId, frequencyDays);
        userData.save(user);
    }

    /**
     * Sets the user's environment, which selects the guideline ranges
     * used for risk scoring and badge evaluation.
     */
    public void setEnvironment(UserDocument user, String newEnvironment) {
        Environment environment;
        try {
            environment = Environment.from(newEnvironment);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown environment: " + newEnvironment);
        }
        user.setEnvironment(environment);
        userData.save(user);
    }

    /**
     * Deletes one logged reading: the most recent one by default, or the
     * one at {@code measurementIndex} in the newest-first order the GET
     * endpoint serves (0 = most recent). The baseline reading of a
     * baseline-relative measurement (the chronologically first one) is
     * protected: it cannot be deleted while other readings exist, and
     * deleting it as the only reading also clears the stored baseline so
     * a re-log starts a fresh one. The last-taken time is recomputed from
     * the remaining history so the reminder math stays correct.
     */
    public void deleteMeasurement(UserDocument user, String measurementId, Integer measurementIndex) {
        Measurement measurement = requireMeasurement(measurementId);
        List<MeasurementSet> stored = user.getMeasurementHistory().get(measurementId);
        if (stored == null || stored.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No readings logged for " + measurementId);
        }
        // Sorted exactly like getMeasurements, so indexes match what GET serves.
        List<MeasurementSet> sorted = new ArrayList<>(stored);
        sorted.sort(Comparator.comparing(MeasurementSet::getTimestamp).reversed());
        int position = measurementIndex != null ? measurementIndex : 0;
        if (position < 0 || position >= sorted.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Invalid measurement index: " + measurementIndex);
        }
        MeasurementSet target = sorted.get(position);

        if (isBaselineRelative(measurement) && target == stored.get(0)) {
            if (stored.size() > 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The baseline reading cannot be deleted while other readings exist");
            }
            // The only reading is going away, so its baseline goes with it.
            user.getMeasurementBaselines().remove(measurementId);
        }

        stored.remove(target);
        if (stored.isEmpty()) {
            user.getMeasurementHistory().remove(measurementId);
            user.getMeasurementLastTaken().remove(measurementId);
        } else {
            user.getMeasurementLastTaken().put(measurementId, stored.stream()
                    .map(MeasurementSet::getTimestamp)
                    .max(Instant::compareTo)
                    .orElseThrow());
        }
        userData.save(user);
    }

    /**
     * Sets how many abnormalities the Crew Medical Officer observed in
     * one logged visual reading: the most recent one by default, or the
     * one at {@code measurementIndex} in the newest-first order the GET
     * endpoint serves (0 = most recent). Only visual measurements can be
     * scored, and the count cannot be negative.
     */
    public void setScore(UserDocument user, String measurementId,
                         Integer measurementIndex, int numAbnormal) {
        Measurement measurement = requireMeasurement(measurementId);
        if (measurement.category() != MeasurementCategory.VISUAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only visual measurements can be scored");
        }
        if (numAbnormal < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "numAbnormal cannot be negative");
        }
        List<MeasurementSet> stored = user.getMeasurementHistory().get(measurementId);
        if (stored == null || stored.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No readings logged for " + measurementId);
        }
        // Sorted exactly like getMeasurements, so indexes match what GET serves.
        List<MeasurementSet> sorted = new ArrayList<>(stored);
        sorted.sort(Comparator.comparing(MeasurementSet::getTimestamp).reversed());
        int position = measurementIndex != null ? measurementIndex : 0;
        if (position < 0 || position >= sorted.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Invalid measurement index: " + measurementIndex);
        }
        sorted.get(position).setNumAbnormal(numAbnormal);
        userData.save(user);
    }

    private boolean isBaselineRelative(Measurement measurement) {
        return measurement.category() == MeasurementCategory.NUMERICAL
                && measurement.baselineMode() != BaselineMode.NONE;
    }

    /**
     * A copy of the set whose values are expressed as the change from the
     * user's baseline (readings without a usable baseline stay raw).
     */
    private MeasurementSet withChangeFromBaseline(UserDocument user, String measurementId,
                                                  MeasurementSet set, BaselineMode mode) {
        if (set.getValues() == null) {
            return set;
        }
        List<Double> converted = new ArrayList<>();
        for (Double raw : set.getValues()) {
            Double change = raw == null ? null : user.changeFromBaseline(measurementId, raw, mode);
            converted.add(change != null ? change : raw);
        }
        return new MeasurementSet(set.getMeasurementId(), set.getUserId(), set.getTimestamp(),
                converted, set.getImages(), set.getNumAbnormal());
    }

    /**
     * The baseline-adjusted average of one set's values: percent change
     * or difference from baseline where applicable, else the raw average.
     */
    private double adjustedAverage(UserDocument user, String measurementId,
                                   MeasurementSet set, Measurement measurement) {
        double raw = average(set.getValues());
        Double change = user.changeFromBaseline(measurementId, raw, measurement.baselineMode());
        return change != null ? change : raw;
    }

    /**
     * Compares the two most recent values. Whether "up" is good or bad
     * depends on the measurement, so the direction stays raw.
     */
    private TrendDirection directionOf(double previous, double latest) {
        double scale = Math.max(Math.abs(previous), Math.abs(latest));
        if (Math.abs(latest - previous) <= FLAT_FRACTION * scale) {
            return TrendDirection.FLAT;
        }
        return latest > previous ? TrendDirection.UP : TrendDirection.DOWN;
    }

    private double average(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private Measurement requireMeasurement(String measurementId) {
        return dataRetrievalService.getMeasurement(measurementId);
    }
}
