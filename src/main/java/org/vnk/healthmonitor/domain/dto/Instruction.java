package org.vnk.healthmonitor.domain.dto;

/**
 * One step of a measurement's instructions, as served to the frontend.
 * Matches the entries in measurementInstructions.json.
 */
public record Instruction(
        String image,
        String text
) {
}
