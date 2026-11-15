package org.vnk.healthmonitor.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.vnk.healthmonitor.domain.dto.BaselineMode;
import org.vnk.healthmonitor.domain.dto.Environment;

/**
 * One user account: username, hashed password, and current token.
 * The document holds, keyed by measurement ID, the measurement data
 * (history of MeasurementSet objects) plus measurement times and
 * frequencies, which drive reminders and scoring.
 */
@Document(collection = "users")
public class UserDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    private String passwordHash;

    @Indexed(unique = true)
    private String token;

    /** Days between reminders, keyed by measurement ID. */
    private Map<String, Integer> measurementFrequencies = new HashMap<>();

    /** When each measurement was last taken, keyed by measurement ID. */
    private Map<String, Instant> measurementLastTaken = new HashMap<>();

    /** Measurement data, keyed by measurement ID. */
    private Map<String, List<MeasurementSet>> measurementHistory = new HashMap<>();

    /** Raw baseline reading per measurement, keyed by measurement ID. */
    private Map<String, Double> measurementBaselines = new HashMap<>();

    /** The user's current environment, stored as its label (e.g. "spacecraft"). */
    private String environment;

    public UserDocument() {
    }

    public UserDocument(String username, String passwordHash, String token) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.token = token;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Map<String, Integer> getMeasurementFrequencies() {
        return measurementFrequencies;
    }

    public void setMeasurementFrequencies(Map<String, Integer> measurementFrequencies) {
        this.measurementFrequencies = measurementFrequencies;
    }

    public Map<String, Instant> getMeasurementLastTaken() {
        return measurementLastTaken;
    }

    public void setMeasurementLastTaken(Map<String, Instant> measurementLastTaken) {
        this.measurementLastTaken = measurementLastTaken;
    }

    public Map<String, List<MeasurementSet>> getMeasurementHistory() {
        return measurementHistory;
    }

    public void setMeasurementHistory(Map<String, List<MeasurementSet>> measurementHistory) {
        this.measurementHistory = measurementHistory;
    }

    public Map<String, Double> getMeasurementBaselines() {
        return measurementBaselines;
    }

    public void setMeasurementBaselines(Map<String, Double> measurementBaselines) {
        this.measurementBaselines = measurementBaselines;
    }

    public Double baselineOf(String measurementId) {
        return measurementBaselines.get(measurementId);
    }

    /**
     * Stores the first raw reading of a measurement as its baseline.
     * Later readings never overwrite it, so the baseline stays the
     * crew member's pre-flight (or first in-flight) reference value.
     */
    public void recordBaselineIfAbsent(String measurementId, double rawValue) {
        measurementBaselines.putIfAbsent(measurementId, rawValue);
    }

    /**
     * Converts a raw reading into its change from the user's baseline,
     * or null when there is no usable baseline and the raw value must be
     * used as-is (legacy data recorded before baselines existed).
     */
    public Double changeFromBaseline(String measurementId, double rawValue, BaselineMode mode) {
        Double baseline = measurementBaselines.get(measurementId);
        if (baseline == null || mode == BaselineMode.NONE || baseline == 0.0) {
            return null;
        }
        return switch (mode) {
            case PERCENT -> (rawValue - baseline) / Math.abs(baseline) * 100.0;
            case DIFFERENCE -> rawValue - baseline;
            case NONE -> null;
        };
    }

    public List<MeasurementSet> historyOf(String measurementId) {
        return measurementHistory.getOrDefault(measurementId, new ArrayList<>());
    }

    public Integer frequencyOf(String measurementId) {
        return measurementFrequencies.get(measurementId);
    }

    public Instant lastTakenOf(String measurementId) {
        return measurementLastTaken.get(measurementId);
    }

    /**
     * The user's current environment, which selects the guideline ranges
     * used for risk scoring and badge evaluation. Documents saved before
     * environments existed default to the spacecraft, the same way a
     * missing frequency falls back to the default.
     */
    public Environment environmentOf() {
        return environment == null ? Environment.SPACECRAFT : Environment.from(environment);
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment.getLabel();
    }
}
