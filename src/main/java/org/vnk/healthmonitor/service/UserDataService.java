package org.vnk.healthmonitor.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.vnk.healthmonitor.domain.dto.LogMeasurementRequest;
import org.vnk.healthmonitor.domain.dto.Measurement;
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
 * Logging a measurement also updates the measurement's last-taken time.
 */
@Service
public class UserDataService {

    private final UserData userData;
    private final DataRetrievalService dataRetrievalService;
    private final Clock clock;

    public UserDataService(UserData userData, DataRetrievalService dataRetrievalService, Clock clock) {
        this.userData = userData;
        this.dataRetrievalService = dataRetrievalService;
        this.clock = clock;
    }

    /**
     * Returns the user's logged measurements for one measurement ID,
     * newest first.
     */
    public List<MeasurementSet> getMeasurements(UserDocument user, String measurementId) {
        requireMeasurement(measurementId);
        List<MeasurementSet> history = new ArrayList<>(user.historyOf(measurementId));
        history.sort(Comparator.comparing(MeasurementSet::getTimestamp).reversed());
        return history;
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
                images = List.of(request.image());
            }
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

    private Measurement requireMeasurement(String measurementId) {
        return dataRetrievalService.getMeasurement(measurementId);
    }
}
