package org.vnk.healthmonitor.controller;

import org.vnk.healthmonitor.domain.dto.LogMeasurementRequest;
import org.vnk.healthmonitor.domain.dto.Reminder;
import org.vnk.healthmonitor.domain.dto.RiskEvaluation;
import org.vnk.healthmonitor.domain.dto.UpdateFrequencyRequest;
import org.vnk.healthmonitor.domain.document.MeasurementSet;
import org.vnk.healthmonitor.domain.document.UserDocument;
import org.vnk.healthmonitor.service.DataRetrievalService;
import org.vnk.healthmonitor.service.JudgementService;
import org.vnk.healthmonitor.service.UserDataService;
import org.vnk.healthmonitor.service.UserLoginService;
import org.vnk.healthmonitor.service.UserReminderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Manages user data, reminders, and risks. The user's token comes from
 * the Authorization header ("Bearer <token>").
 */
@RestController
public class UserController {

    private final UserDataService userDataService;
    private final UserLoginService userLoginService;
    private final UserReminderService userReminderService;
    private final JudgementService judgementService;
    private final DataRetrievalService dataRetrievalService;

    public UserController(UserDataService userDataService,
                          UserLoginService userLoginService,
                          UserReminderService userReminderService,
                          JudgementService judgementService,
                          DataRetrievalService dataRetrievalService) {
        this.userDataService = userDataService;
        this.userLoginService = userLoginService;
        this.userReminderService = userReminderService;
        this.judgementService = judgementService;
        this.dataRetrievalService = dataRetrievalService;
    }

    /**
     * Gets the user's measurements from the database.
     */
    @GetMapping("/users/measurements/{measurementID}")
    public List<MeasurementSet> getMeasurements(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("measurementID") String measurementId) {
        UserDocument user = userLoginService.resolveUser(authorization);
        return userDataService.getMeasurements(user, measurementId);
    }

    /**
     * Logs the user's measurements in the database.
     */
    @PostMapping("/users/measurements/{measurementID}")
    public void logMeasurement(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("measurementID") String measurementId,
            @RequestBody LogMeasurementRequest request) {
        UserDocument user = userLoginService.resolveUser(authorization);
        userDataService.logMeasurement(user, measurementId, request);
    }

    /**
     * Updates the reminder frequency (days between measurements) for one
     * measurement. Supports the frontend's frequency dropdown.
     */
    @PutMapping("/users/measurements/{measurementID}/frequency")
    public void setFrequency(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("measurementID") String measurementId,
            @RequestBody @jakarta.validation.Valid UpdateFrequencyRequest request) {
        UserDocument user = userLoginService.resolveUser(authorization);
        userDataService.setFrequency(user, measurementId, request.frequencyDays());
    }

    /**
     * Lists the upcoming measurements the user needs to take in a time
     * period, ordered by date to be taken.
     */
    @GetMapping("/users/upcoming")
    public List<Reminder> getUpcoming(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(name = "withinDays", defaultValue = "30") int withinDays) {
        UserDocument user = userLoginService.resolveUser(authorization);
        return userReminderService.getUpcoming(user, withinDays);
    }

    /**
     * Returns all of a user's risk scores.
     */
    @GetMapping("/users/risks")
    public List<RiskEvaluation> getRisks(@RequestHeader("Authorization") String authorization) {
        UserDocument user = userLoginService.resolveUser(authorization);
        return judgementService.evaluateAll(user);
    }

    /**
     * Returns a user's risk score, trends, and advices.
     */
    @GetMapping("/users/risks/{riskID}")
    public RiskEvaluation getRisk(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("riskID") String riskId) {
        UserDocument user = userLoginService.resolveUser(authorization);
        return judgementService.evaluate(user, dataRetrievalService.getRisk(riskId));
    }
}
