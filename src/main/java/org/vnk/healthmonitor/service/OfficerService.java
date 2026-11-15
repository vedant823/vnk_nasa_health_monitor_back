package org.vnk.healthmonitor.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.vnk.healthmonitor.domain.dto.ChildOverview;
import org.vnk.healthmonitor.domain.dto.ChildReminder;
import org.vnk.healthmonitor.domain.dto.Reminder;
import org.vnk.healthmonitor.domain.dto.RiskEvaluation;
import org.vnk.healthmonitor.domain.dto.UserStatus;
import org.vnk.healthmonitor.domain.document.MeasurementSet;
import org.vnk.healthmonitor.domain.document.UserDocument;
import org.vnk.healthmonitor.repository.UserData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The plumbing behind the officer-child relationship: verifies that a
 * token-holding officer may access the requested child, then delegates
 * to the existing data, scoring, and reminder services with the
 * child's document. No analysis logic lives here — officers get the
 * same read + score view of a child's data that the child gets of
 * their own, and nothing more.
 */
@Service
public class OfficerService {

    private final UserData userData;
    private final UserDataService userDataService;
    private final JudgementService judgementService;
    private final DataRetrievalService dataRetrievalService;
    private final UserReminderService userReminderService;

    public OfficerService(UserData userData,
                          UserDataService userDataService,
                          JudgementService judgementService,
                          DataRetrievalService dataRetrievalService,
                          UserReminderService userReminderService) {
        this.userData = userData;
        this.userDataService = userDataService;
        this.judgementService = judgementService;
        this.dataRetrievalService = dataRetrievalService;
        this.userReminderService = userReminderService;
    }

    /**
     * Lists the officer's children with enough data for a home overview.
     */
    public List<ChildOverview> listChildren(UserDocument officer) {
        return userData.findByOfficerId(officer.getId()).stream()
                .map(child -> new ChildOverview(child.getId(), child.getUsername(),
                        latestMeasurementOf(child)))
                .toList();
    }

    /**
     * Every risk score of a child, computed exactly as the child's own
     * GET /users/risks would compute them.
     */
    public List<RiskEvaluation> getRisks(UserDocument officer, String childId) {
        return judgementService.evaluateAll(resolveChild(officer, childId));
    }

    /**
     * One risk score of a child.
     */
    public RiskEvaluation getRisk(UserDocument officer, String childId, String riskId) {
        return judgementService.evaluate(resolveChild(officer, childId),
                dataRetrievalService.getRisk(riskId));
    }

    /**
     * The child's logged history for one measurement, newest first —
     * the officer's view for reviewing visual scans.
     */
    public List<MeasurementSet> getMeasurements(UserDocument officer, String childId,
                                                String measurementId) {
        return userDataService.getMeasurements(resolveChild(officer, childId), measurementId);
    }

    /**
     * The officer's CMO review of one of the child's visual readings,
     * with the same semantics as the child's own score endpoint. The
     * officer must not have an officer of their own — the same rule the
     * user-scoped endpoint enforces on self-scoring.
     */
    public void setScore(UserDocument officer, String childId, String measurementId,
                         Integer measurementIndex, int numAbnormal) {
        userDataService.setScore(officer, resolveChild(officer, childId), measurementId,
                measurementIndex, numAbnormal);
    }

    /**
     * The overdue measurements across all of the officer's children,
     * each tagged with the child it belongs to. Only medical officers
     * can call this.
     */
    public List<ChildReminder> getOverdue(UserDocument officer) {
        if (officer.statusOf() != UserStatus.MEDICAL_OFFICER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only officers can list overdue measurements");
        }
        List<ChildReminder> overdue = new ArrayList<>();
        for (UserDocument child : userData.findByOfficerId(officer.getId())) {
            for (Reminder reminder : userReminderService.getOverdue(child)) {
                overdue.add(new ChildReminder(child.getId(), reminder.measurementId(),
                        reminder.dueDate()));
            }
        }
        overdue.sort(Comparator.comparing(ChildReminder::dueDate));
        return overdue;
    }

    /**
     * The child's due/overdue reminders, so the officer sees who is
     * behind on their baseline checklist.
     */
    public List<Reminder> getUpcoming(UserDocument officer, String childId, int withinDays) {
        return userReminderService.getUpcoming(resolveChild(officer, childId), withinDays);
    }

    /**
     * Loads the child and verifies the relationship: unknown children
     * are 404 (matching the spec's unknown-ID style), children assigned
     * to someone else (or to nobody) are 403.
     */
    private UserDocument resolveChild(UserDocument officer, String childId) {
        UserDocument child = userData.findById(childId).orElse(null);
        if (child == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown child: " + childId);
        }
        if (!officer.getId().equals(child.officerIdOf())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This user is not your child");
        }
        return child;
    }

    private java.time.Instant latestMeasurementOf(UserDocument child) {
        return child.getMeasurementLastTaken().values().stream()
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
