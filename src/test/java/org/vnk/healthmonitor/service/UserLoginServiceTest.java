package org.vnk.healthmonitor.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.vnk.healthmonitor.domain.dto.LoginRequest;
import org.vnk.healthmonitor.domain.document.UserDocument;
import org.vnk.healthmonitor.repository.UserData;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * Unit tests for login and account creation, with a mocked repository.
 */
class UserLoginServiceTest {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UserData userData;
    private UserLoginService service;

    @BeforeEach
    void setUp() {
        userData = mock(UserData.class);
        DataRetrievalService dataRetrievalService = mock(DataRetrievalService.class);
        when(dataRetrievalService.getMeasurementIds()).thenReturn(List.of("m1", "m2"));
        service = new UserLoginService(userData, dataRetrievalService, 7);
    }

    private ResponseStatusException exceptionOf(Runnable action) {
        try {
            action.run();
        } catch (ResponseStatusException e) {
            return e;
        }
        throw new AssertionError("Expected ResponseStatusException");
    }

    private UserDocument existingUser(String username, String password) {
        return new UserDocument(username, passwordEncoder.encode(password), "existing-token");
    }

    @Test
    void createAccountReturnsTokenAndInitializesFrequencies() {
        when(userData.findByUsername("alice")).thenReturn(Optional.empty());

        String token = service.createAccount(new LoginRequest("alice", "secret"));

        assertNotNull(token);
        ArgumentCaptor<UserDocument> captor = ArgumentCaptor.forClass(UserDocument.class);
        verify(userData).save(captor.capture());
        UserDocument saved = captor.getValue();
        assertEquals("alice", saved.getUsername());
        assertTrue(passwordEncoder.matches("secret", saved.getPasswordHash()));
        assertEquals(token, saved.getToken());
        // Every known measurement gets the configured default frequency.
        assertEquals(7, saved.frequencyOf("m1"));
        assertEquals(7, saved.frequencyOf("m2"));
        // New accounts start on the spacecraft.
        assertEquals(org.vnk.healthmonitor.domain.dto.Environment.SPACECRAFT, saved.environmentOf());
    }

    @Test
    void createAccountRejectsDuplicateUsername() {
        when(userData.findByUsername("alice")).thenReturn(
                Optional.of(existingUser("alice", "secret")));

        ResponseStatusException e = exceptionOf(() ->
                service.createAccount(new LoginRequest("alice", "other")));

        assertEquals(CONFLICT, e.getStatusCode());
        verify(userData, never()).save(any());
    }

    @Test
    void loginReturnsTokenForCorrectCredentials() {
        when(userData.findByUsername("alice")).thenReturn(
                Optional.of(existingUser("alice", "secret")));

        String token = service.login(new LoginRequest("alice", "secret"));

        assertEquals("existing-token", token);
    }

    @Test
    void loginRejectsUnknownUsername() {
        when(userData.findByUsername("ghost")).thenReturn(Optional.empty());

        ResponseStatusException e = exceptionOf(() ->
                service.login(new LoginRequest("ghost", "secret")));

        assertEquals(UNAUTHORIZED, e.getStatusCode());
    }

    @Test
    void loginRejectsWrongPassword() {
        when(userData.findByUsername("alice")).thenReturn(
                Optional.of(existingUser("alice", "secret")));

        ResponseStatusException e = exceptionOf(() ->
                service.login(new LoginRequest("alice", "wrong")));

        assertEquals(UNAUTHORIZED, e.getStatusCode());
    }

    @Test
    void resolveUserStripsBearerPrefix() {
        when(userData.findByToken("existing-token")).thenReturn(
                Optional.of(existingUser("alice", "secret")));

        UserDocument user = service.resolveUser("Bearer existing-token");

        assertEquals("alice", user.getUsername());
    }

    @Test
    void resolveUserAcceptsRawToken() {
        when(userData.findByToken("existing-token")).thenReturn(
                Optional.of(existingUser("alice", "secret")));

        UserDocument user = service.resolveUser("existing-token");

        assertEquals("alice", user.getUsername());
    }

    @Test
    void resolveUserRejectsMissingHeader() {
        ResponseStatusException e = exceptionOf(() -> service.resolveUser(null));

        assertEquals(UNAUTHORIZED, e.getStatusCode());
    }

    @Test
    void resolveUserRejectsBlankHeader() {
        ResponseStatusException e = exceptionOf(() -> service.resolveUser("   "));

        assertEquals(UNAUTHORIZED, e.getStatusCode());
    }

    @Test
    void resolveUserRejectsUnknownToken() {
        when(userData.findByToken("nope")).thenReturn(Optional.empty());

        ResponseStatusException e = exceptionOf(() -> service.resolveUser("Bearer nope"));

        assertEquals(UNAUTHORIZED, e.getStatusCode());
    }
}
