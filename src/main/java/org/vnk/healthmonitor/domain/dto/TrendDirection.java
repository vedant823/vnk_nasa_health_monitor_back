package org.vnk.healthmonitor.domain.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Lightweight last-vs-previous direction of a measurement's history:
 * no statistical modelling, just a comparison of the two most recent
 * readings. Null (absent) means there is not enough history yet.
 */
public enum TrendDirection {
    UP("up"),
    DOWN("down"),
    FLAT("flat");

    private final String label;

    TrendDirection(String label) {
        this.label = label;
    }

    @JsonCreator
    public static TrendDirection from(String value) {
        for (TrendDirection direction : values()) {
            if (direction.label.equalsIgnoreCase(value)) {
                return direction;
            }
        }
        throw new IllegalArgumentException("Unknown trend direction: " + value);
    }

    @JsonValue
    public String getLabel() {
        return label;
    }
}
