package org.vnk.healthmonitor.domain.document;

import java.time.Instant;
import java.util.List;

/**
 * One logged measurement event. Exactly one of {@code values}
 * (numerical measurements) or {@code images} (visual measurements,
 * base64-encoded) is populated, matching the measurement's category.
 */
public class MeasurementSet {

    private String measurementId;

    private String userId;

    private Instant timestamp;

    private List<Double> values;

    private List<String> images;

    public MeasurementSet() {
    }

    public MeasurementSet(String measurementId, String userId, Instant timestamp,
                          List<Double> values, List<String> images) {
        this.measurementId = measurementId;
        this.userId = userId;
        this.timestamp = timestamp;
        this.values = values;
        this.images = images;
    }

    public String getMeasurementId() {
        return measurementId;
    }

    public void setMeasurementId(String measurementId) {
        this.measurementId = measurementId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public List<Double> getValues() {
        return values;
    }

    public void setValues(List<Double> values) {
        this.values = values;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }
}
