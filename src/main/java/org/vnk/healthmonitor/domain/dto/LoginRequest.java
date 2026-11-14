package org.vnk.healthmonitor.domain.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Credentials submitted to either login or create an account.
 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
