package org.vnk.healthmonitor.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.vnk.healthmonitor.domain.dto.LoginRequest;
import org.vnk.healthmonitor.domain.document.UserDocument;
import org.vnk.healthmonitor.repository.UserData;

import java.util.UUID;

/**
 * Handles the login procedure and creates new accounts.
 */
@Service
public class UserLoginService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final UserData userData;
    private final DataRetrievalService dataRetrievalService;
    private final int defaultFrequencyDays;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserLoginService(UserData userData,
                            DataRetrievalService dataRetrievalService,
                            @org.springframework.beans.factory.annotation.Value("${app.defaults.measurement-frequency-days}") int defaultFrequencyDays) {
        this.userData = userData;
        this.dataRetrievalService = dataRetrievalService;
        this.defaultFrequencyDays = defaultFrequencyDays;
    }

    /**
     * Creates a new account, initializing every measurement's reminder
     * frequency to the default, and returns its token.
     */
    public String createAccount(LoginRequest request) {
        if (userData.findByUsername(request.username()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
        }
        String token = UUID.randomUUID().toString();
        UserDocument user = new UserDocument(request.username(), passwordEncoder.encode(request.password()), token);
        for (String measurementId : dataRetrievalService.getMeasurementIds()) {
            user.getMeasurementFrequencies().put(measurementId, defaultFrequencyDays);
        }
        userData.save(user);
        return token;
    }

    /**
     * Verifies credentials and returns the account's token.
     */
    public String login(LoginRequest request) {
        UserDocument user = userData.findByUsername(request.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        return user.getToken();
    }

    /**
     * Resolves the Bearer token from the Authorization header to a user.
     */
    public UserDocument resolveUser(String authorizationHeader) {
        String token = authorizationHeader;
        if (token != null && token.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            token = token.substring(BEARER_PREFIX.length()).trim();
        }
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing token");
        }
        return userData.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"));
    }
}
