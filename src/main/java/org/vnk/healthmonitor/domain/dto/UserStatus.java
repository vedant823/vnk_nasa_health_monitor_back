package org.vnk.healthmonitor.domain.dto;

/**
 * The user's role on the crew. A medical officer can have children
 * (crewmates whose data they review) and judge visual measurements; a
 * normal user can have an officer. The hierarchy is one level deep.
 */
public enum UserStatus {

    NORMAL("normal"),
    MEDICAL_OFFICER("medical officer");

    private final String label;

    UserStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static UserStatus from(String label) {
        for (UserStatus status : values()) {
            if (status.label.equalsIgnoreCase(label.trim())) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status: " + label);
    }
}
