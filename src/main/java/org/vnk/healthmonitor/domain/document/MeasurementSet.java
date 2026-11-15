package org.vnk.healthmonitor.domain.document;

import java.time.Instant;
import java.util.List;

/**
 * One logged measurement event. Exactly one of {@code values}
 * (numerical measurements) or {@code images} (visual measurements,
 * base64-encoded) is populated, matching the measurement's category.
 * For visual measurements, {@code numAbnormal} optionally records how
 * many flags the Crew Medical Officer observed in the image
 * (null = not yet reviewed; 0 = reviewed and normal).
 */
public class MeasurementSet {

    private String measurementId;

    private String userId;

    private Instant timestamp;

    private List<Double> values;

    private List<String> images;

    private Integer numAbnormal;

    public MeasurementSet() {
    }

    public MeasurementSet(String measurementId, String userId, Instant timestamp,
                          List<Double> values, List<String> images) {
        this(measurementId, userId, timestamp, values, images, null);
    }

    public MeasurementSet(String measurementId, String userId, Instant timestamp,
                          List<Double> values, List<String> images, Integer numAbnormal) {
        this.measurementId = measurementId;
        this.userId = userId;
        this.timestamp = timestamp;
        this.values = values;
        this.images = images;
        this.numAbnormal = numAbnormal;
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

    public Integer getNumAbnormal() {
        return numAbnormal;
    }

    public void setNumAbnormal(Integer numAbnormal) {
        this.numAbnormal = numAbnormal;
    }
}
