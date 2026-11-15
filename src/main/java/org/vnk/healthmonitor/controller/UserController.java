package org.vnk.healthmonitor.controller;

import org.vnk.healthmonitor.domain.dto.LogMeasurementRequest;
import org.vnk.healthmonitor.domain.dto.BadgeSet;
import org.vnk.healthmonitor.domain.dto.MeasurementTrend;
import org.vnk.healthmonitor.domain.dto.Reminder;
import org.vnk.healthmonitor.domain.dto.ReminderDigest;
import org.vnk.healthmonitor.domain.dto.RiskEvaluation;
import org.vnk.healthmonitor.domain.dto.UpdateFrequencyRequest;
import org.vnk.healthmonitor.domain.document.MeasurementSet;
import org.vnk.healthmonitor.domain.document.UserDocument;
import org.vnk.healthmonitor.service.DataRetrievalService;
import org.vnk.healthmonitor.service.BadgeService;
import org.vnk.healthmonitor.service.JudgementService;
import org.vnk.healthmonitor.service.NotificationScheduler;
import org.vnk.healthmonitor.service.UserDataService;
import org.vnk.healthmonitor.service.UserLoginService;
import org.vnk.healthmonitor.service.UserReminderService;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final NotificationScheduler notificationScheduler;
    private final BadgeService badgeService;

    public UserController(UserDataService userDataService,
                          UserLoginService userLoginService,
                          UserReminderService userReminderService,
                          JudgementService judgementService,
                          DataRetrievalService dataRetrievalService,
                          NotificationScheduler notificationScheduler,
                          BadgeService badgeService) {
        this.userDataService = userDataService;
        this.userLoginService = userLoginService;
        this.userReminderService = userReminderService;
        this.judgementService = judgementService;
        this.dataRetrievalService = dataRetrievalService;
        this.notificationScheduler = notificationScheduler;
        this.badgeService = badgeService;
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
     * Deletes the user's most recent reading of a measurement, or the
     * reading at the given newest-first index (0 = most recent).
     */
    @DeleteMapping("/users/measurements/{measurementID}")
    public void deleteMeasurement(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("measurementID") String measurementId,
            @RequestParam(name = "measurementIndex", required = false) Integer measurementIndex) {
        UserDocument user = userLoginService.resolveUser(authorization);
        userDataService.deleteMeasurement(user, measurementId, measurementIndex);
    }

    /**
     * Sets how many abnormalities the Crew Medical Officer observed in
     * one logged visual measurement: the most recent one by default, or
     * the one at the given newest-first index (0 = most recent). Only a
     * user without an officer can judge images.
     */
    @PutMapping("/users/measurements/{measurementID}/score")
    public void setScore(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("measurementID") String measurementId,
            @RequestParam("numAbnormal") int numAbnormal,
            @RequestParam(name = "measurementIndex", required = false) Integer measurementIndex) {
        UserDocument user = userLoginService.resolveUser(authorization);
        userDataService.setScore(user, user, measurementId, measurementIndex, numAbnormal);
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

    /**
     * Lists the user's earned badges and, for the unearned ones, the
     * badge plus per-criterion progress toward it.
     */
    @GetMapping("/users/badges")
    public BadgeSet getBadges(@RequestHeader("Authorization") String authorization) {
        UserDocument user = userLoginService.resolveUser(authorization);
        return badgeService.evaluate(user);
    }

    /**
     * Sets (or changes, at will) the officer supervising this user. The
     * named user must be a medical officer without an officer of their
     * own.
     */
    @PutMapping("/users/officer")
    public void setOfficer(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("newOfficer") String newOfficer) {
        UserDocument user = userLoginService.resolveUser(authorization);
        userDataService.setOfficer(user, newOfficer);
    }

    /**
     * Returns the username of the user's officer, or the literal "null"
     * when the user is standalone (String endpoints here serve raw text,
     * so the null case needs the explicit marker).
     */
    @GetMapping("/users/officer")
    public String getOfficer(@RequestHeader("Authorization") String authorization) {
        UserDocument user = userLoginService.resolveUser(authorization);
        String officer = userDataService.officerOf(user);
        return officer != null ? officer : "null";
    }

    /**
     * Sets the user's status ("normal" or "medical officer"), which
     * determines whether they can have children or an officer and judge
     * images. The status cannot change while a relationship exists.
     */
    @PutMapping("/users/status")
    public void setStatus(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("newStatus") String newStatus) {
        UserDocument user = userLoginService.resolveUser(authorization);
        userDataService.setStatus(user, newStatus);
    }

    /**
     * Returns the user's current status. New accounts default to
     * "normal".
     */
    @GetMapping("/users/status")
    public String getStatus(@RequestHeader("Authorization") String authorization) {
        UserDocument user = userLoginService.resolveUser(authorization);
        return user.statusOf().getLabel();
    }

    /**
     * Returns the user's current environment, which selects the guideline
     * ranges used for risk scoring and badge evaluation.
     */
    @GetMapping("/users/environment")
    public String getEnvironment(@RequestHeader("Authorization") String authorization) {
        UserDocument user = userLoginService.resolveUser(authorization);
        return user.environmentOf().getLabel();
    }

    /**
     * Sets the user's environment. Invalid values are rejected with 400.
     */
    @PutMapping("/users/environment")
    public void setEnvironment(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("newEnvironment") String newEnvironment) {
        UserDocument user = userLoginService.resolveUser(authorization);
        userDataService.setEnvironment(user, newEnvironment);
    }

    /**
     * Returns the direction of a measurement's two most recent readings
     * (up, down, or flat; null when there is not enough history).
     */
    @GetMapping("/users/measurements/{measurementID}/trend")
    public MeasurementTrend getTrend(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("measurementID") String measurementId) {
        UserDocument user = userLoginService.resolveUser(authorization);
        return userDataService.getTrend(user, measurementId);
    }

    /**
     * Returns the user's daily/weekly/monthly reminder digest — the same
     * buckets the scheduled notification sender uses.
     */
    @GetMapping("/users/digest")
    public ReminderDigest getDigest(@RequestHeader("Authorization") String authorization) {
        UserDocument user = userLoginService.resolveUser(authorization);
        return notificationScheduler.buildDigest(user);
    }
}
