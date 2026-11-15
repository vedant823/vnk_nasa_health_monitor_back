package org.vnk.healthmonitor.controller;

import org.vnk.healthmonitor.domain.dto.ChildOverview;
import org.vnk.healthmonitor.domain.dto.ChildReminder;
import org.vnk.healthmonitor.domain.dto.Reminder;
import org.vnk.healthmonitor.domain.dto.RiskEvaluation;
import org.vnk.healthmonitor.domain.document.MeasurementSet;
import org.vnk.healthmonitor.domain.document.UserDocument;
import org.vnk.healthmonitor.service.OfficerService;
import org.vnk.healthmonitor.service.UserLoginService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The officer's view of their children: read-only health data plus the
 * CMO score entry for visual readings. The officer's token comes from
 * the Authorization header ("Bearer <token>"); a child assigned to
 * someone else is 403.
 */
@RestController
public class OfficerController {

    private final UserLoginService userLoginService;
    private final OfficerService officerService;

    public OfficerController(UserLoginService userLoginService, OfficerService officerService) {
        this.userLoginService = userLoginService;
        this.officerService = officerService;
    }

    /**
     * Lists the officer's children with their latest activity.
     */
    @GetMapping("/users/children")
    public List<ChildOverview> getChildren(@RequestHeader("Authorization") String authorization) {
        return officerService.listChildren(userLoginService.resolveUser(authorization));
    }

    /**
     * All risk scores of a child.
     */
    @GetMapping("/users/children/{childID}/risks")
    public List<RiskEvaluation> getChildRisks(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("childID") String childId) {
        return officerService.getRisks(userLoginService.resolveUser(authorization), childId);
    }

    /**
     * One risk score of a child.
     */
    @GetMapping("/users/children/{childID}/risks/{riskID}")
    public RiskEvaluation getChildRisk(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("childID") String childId,
            @PathVariable("riskID") String riskId) {
        return officerService.getRisk(userLoginService.resolveUser(authorization), childId, riskId);
    }

    /**
     * The child's logged history for one measurement, newest first.
     */
    @GetMapping("/users/children/{childID}/measurements/{measurementID}")
    public List<MeasurementSet> getChildMeasurements(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("childID") String childId,
            @PathVariable("measurementID") String measurementId) {
        return officerService.getMeasurements(userLoginService.resolveUser(authorization),
                childId, measurementId);
    }

    /**
     * The officer's CMO review of one of the child's visual readings.
     */
    @PutMapping("/users/children/{childID}/measurements/{measurementID}/score")
    public void setChildScore(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("childID") String childId,
            @PathVariable("measurementID") String measurementId,
            @RequestParam("numAbnormal") int numAbnormal,
            @RequestParam(name = "measurementIndex", required = false) Integer measurementIndex) {
        UserDocument officer = userLoginService.resolveUser(authorization);
        officerService.setScore(officer, childId, measurementId, measurementIndex, numAbnormal);
    }

    /**
     * The overdue measurements across all of the officer's children.
     */
    @GetMapping("/users/overdue")
    public List<ChildReminder> getOverdue(@RequestHeader("Authorization") String authorization) {
        return officerService.getOverdue(userLoginService.resolveUser(authorization));
    }

    /**
     * The child's due/overdue reminders.
     */
    @GetMapping("/users/children/{childID}/upcoming")
    public List<Reminder> getChildUpcoming(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("childID") String childId,
            @RequestParam(name = "withinDays", defaultValue = "30") int withinDays) {
        return officerService.getUpcoming(userLoginService.resolveUser(authorization),
                childId, withinDays);
    }
}
