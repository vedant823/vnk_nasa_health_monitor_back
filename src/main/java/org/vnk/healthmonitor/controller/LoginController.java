package org.vnk.healthmonitor.controller;

import org.vnk.healthmonitor.domain.dto.LoginRequest;
import org.vnk.healthmonitor.service.UserLoginService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manages user logins (login credentials in body).
 */
@RestController
@Validated
public class LoginController {

    private final UserLoginService userLoginService;

    public LoginController(UserLoginService userLoginService) {
        this.userLoginService = userLoginService;
    }

    /**
     * Creates a new account, returning the token.
     */
    @PostMapping("/users/login")
    public String createAccount(@RequestBody @Validated LoginRequest request) {
        return userLoginService.createAccount(request);
    }

    /**
     * Verifies credentials and returns the account's token. PUT (not GET)
     * because login carries a credentials body and GET bodies cannot be
     * sent by browsers; PUT is the fitting idempotent method.
     */
    @PutMapping("/users/login")
    public String login(@RequestBody @Validated LoginRequest request) {
        return userLoginService.login(request);
    }
}
