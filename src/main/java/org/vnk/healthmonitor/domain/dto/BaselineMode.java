package org.vnk.healthmonitor.domain.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How a numerical measurement relates to a user's baseline reading.
 * PERCENT converts raw readings to percent change from baseline
 * (e.g. body mass), DIFFERENCE to the plain delta in the measurement's
 * unit (e.g. retinal thickness in micrometres), and NONE means the
 * reading is absolute and needs no baseline.
 */
public enum BaselineMode {
    NONE("none"),
    PERCENT("percent"),
    DIFFERENCE("difference");

    private final String label;

    BaselineMode(String label) {
        this.label = label;
    }

    @JsonCreator
    public static BaselineMode from(String value) {
        for (BaselineMode mode : values()) {
            if (mode.label.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown baseline mode: " + value);
    }

    @JsonValue
    public String getLabel() {
        return label;
    }
}
