package org.vnk.healthmonitor.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.vnk.healthmonitor.service.UserLoginService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for LoginController: routes, status codes, and that the
 * credentials body is passed through to the service. The service is mocked.
 */
class LoginControllerTest {

    private UserLoginService userLoginService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userLoginService = mock(UserLoginService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new LoginController(userLoginService)).build();
    }

    @Test
    void createAccountReturnsToken() throws Exception {
        when(userLoginService.createAccount(new org.vnk.healthmonitor.domain.dto.LoginRequest("alice", "secret")))
                .thenReturn("token-123");

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("token-123"));
    }

    @Test
    void duplicateUsernameReturns409() throws Exception {
        when(userLoginService.createAccount(new org.vnk.healthmonitor.domain.dto.LoginRequest("alice", "secret")))
                .thenThrow(new ResponseStatusException(CONFLICT, "Username already taken"));

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"secret\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void loginReturnsToken() throws Exception {
        when(userLoginService.login(new org.vnk.healthmonitor.domain.dto.LoginRequest("alice", "secret")))
                .thenReturn("token-123");

        // PUT with a credentials body: GET bodies cannot be sent by
        // browsers, and login is idempotent, so PUT is the fitting method.
        mockMvc.perform(put("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("token-123"));
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        when(userLoginService.login(new org.vnk.healthmonitor.domain.dto.LoginRequest("alice", "wrong")))
                .thenThrow(new ResponseStatusException(UNAUTHORIZED, "Invalid username or password"));

        mockMvc.perform(put("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }
}
