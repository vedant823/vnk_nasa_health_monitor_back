package org.vnk.healthmonitor.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public List<MeasurementSet> historyOf(String measurementId) {
        return measurementHistory.getOrDefault(measurementId, new ArrayList<>());
    }

    public Integer frequencyOf(String measurementId) {
        return measurementFrequencies.get(measurementId);
    }

    public Instant lastTakenOf(String measurementId) {
        return measurementLastTaken.get(measurementId);
    }
}
