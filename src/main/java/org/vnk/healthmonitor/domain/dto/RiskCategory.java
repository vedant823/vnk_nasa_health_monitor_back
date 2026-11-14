package org.vnk.healthmonitor.domain.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RiskCategory {
    PHYSICAL("physical"),
    PSYCHOLOGICAL("psychological");

    private final String label;

    RiskCategory(String label) {
        this.label = label;
    }

    @JsonCreator
    public static RiskCategory from(String value) {
        for (RiskCategory category : values()) {
            if (category.label.equalsIgnoreCase(value)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown risk category: " + value);
    }

    @JsonValue
    public String getLabel() {
        return label;
    }
}
