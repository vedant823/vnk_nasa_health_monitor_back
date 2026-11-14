package org.vnk.healthmonitor.domain.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MeasurementCategory {
    NUMERICAL("numerical"),
    VISUAL("visual");

    private final String label;

    MeasurementCategory(String label) {
        this.label = label;
    }

    @JsonCreator
    public static MeasurementCategory from(String value) {
        for (MeasurementCategory category : values()) {
            if (category.label.equalsIgnoreCase(value)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown measurement category: " + value);
    }

    @JsonValue
    public String getLabel() {
        return label;
    }
}
