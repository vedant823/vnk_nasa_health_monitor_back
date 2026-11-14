package org.vnk.healthmonitor.domain.dto;

/**
 * A user account without persistence details, as handled by the login
 * service: username, hashed password, and current token.
 */
public record User(
        String username,
        String hashedPassword,
        String token
) {
}
