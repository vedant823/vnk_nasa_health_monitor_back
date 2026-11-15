package org.vnk.healthmonitor.domain.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The situation the user is in, which selects the guideline ranges used
 * for risk scoring and badge evaluation: a spacecraft in transit, a
 * space station in low Earth orbit, a pressurized rover, a Moon base,
 * or a Mars base.
 */
public enum Environment {
    SPACECRAFT("spacecraft"),
    STATION("station"),
    ROVER("rover"),
    MOON("moon"),
    MARS("mars");

    private final String label;

    Environment(String label) {
        this.label = label;
    }

    @JsonCreator
    public static Environment from(String value) {
        for (Environment environment : values()) {
            if (environment.label.equalsIgnoreCase(value)) {
                return environment;
            }
        }
        throw new IllegalArgumentException("Unknown environment: " + value);
    }

    @JsonValue
    public String getLabel() {
        return label;
    }
}
