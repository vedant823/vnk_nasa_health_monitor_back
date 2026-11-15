package org.vnk.healthmonitor.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.vnk.healthmonitor.domain.dto.ChildOverview;
import org.vnk.healthmonitor.domain.dto.ChildReminder;
import org.vnk.healthmonitor.domain.dto.Reminder;
import org.vnk.healthmonitor.domain.dto.RiskEvaluation;
import org.vnk.healthmonitor.domain.dto.RiskFactor;
import org.vnk.healthmonitor.domain.document.MeasurementSet;
import org.vnk.healthmonitor.domain.document.UserDocument;
import org.vnk.healthmonitor.repository.UserData;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the officer-child plumbing: the 403/404 relationship
 * checks, the children overview, and delegation to the existing
 * services with the child's document.
 */
class OfficerServiceTest {

    private UserData userData;
    private UserDataService userDataService;
    private JudgementService judgementService;
    private DataRetrievalService dataRetrievalService;
    private UserReminderService userReminderService;
    private OfficerService officerService;
    private UserDocument officer;
    private UserDocument child;

    @BeforeEach
    void setUp() {
        userData = mock(UserData.class);
        userDataService = mock(UserDataService.class);
        judgementService = mock(JudgementService.class);
        dataRetrievalService = mock(DataRetrievalService.class);
        userReminderService = mock(UserReminderService.class);
        officerService = new OfficerService(userData, userDataService, judgementService,
                dataRetrievalService, userReminderService);

        officer = new UserDocument("cmo", "hash", "officer-token");
        officer.setId("officer-1");
        child = new UserDocument("kid", "hash", "child-token");
        child.setId("child-1");
        child.setOfficerId("officer-1");
    }

    private void givenChildIsFindable() {
        when(userData.findById("child-1")).thenReturn(Optional.of(child));
    }

    @Test
    void listChildrenMapsTheOverviews() {
        child.getMeasurementLastTaken().put("wbc", Instant.parse("2026-01-10T12:00:00Z"));
        child.getMeasurementLastTaken().put("hr", Instant.parse("2026-01-11T12:00:00Z"));
        when(userData.findByOfficerId("officer-1")).thenReturn(List.of(child));

        List<ChildOverview> children = officerService.listChildren(officer);

        assertEquals(1, children.size());
        assertEquals("child-1", children.get(0).userId());
        assertEquals("kid", children.get(0).username());
        // The most recent of the child's last-taken times wins.
        assertEquals(Instant.parse("2026-01-11T12:00:00Z"), children.get(0).latestMeasurement());
    }

    @Test
    void childWithoutReadingsHasANullLatestMeasurement() {
        when(userData.findByOfficerId("officer-1")).thenReturn(List.of(child));

        assertNull(officerService.listChildren(officer).get(0).latestMeasurement());
    }

    @Test
    void officerWithoutChildrenGetsAnEmptyList() {
        when(userData.findByOfficerId("officer-1")).thenReturn(List.of());

        assertEquals(0, officerService.listChildren(officer).size());
    }

    @Test
    void unknownChildReturns404() {
        when(userData.findById("nope")).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> officerService.getRisks(officer, "nope"));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void childOfAnotherOfficerReturns403() {
        child.setOfficerId("officer-2");
        givenChildIsFindable();

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> officerService.getRisks(officer, "child-1"));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    @Test
    void unassignedChildReturns403() {
        child.setOfficerId(null);
        givenChildIsFindable();

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> officerService.getRisks(officer, "child-1"));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    @Test
    void getRisksDelegatesWithTheChildsDocument() {
        givenChildIsFindable();
        List<RiskEvaluation> evaluations = List.of(
                new RiskEvaluation("r1", 100, List.of("No action needed"), "reason"));
        when(judgementService.evaluateAll(child)).thenReturn(evaluations);

        assertEquals(evaluations, officerService.getRisks(officer, "child-1"));
        verify(judgementService).evaluateAll(child);
    }

    @Test
    void getRiskDelegatesWithTheChildsDocumentAndTheRisk() {
        givenChildIsFindable();
        RiskFactor risk = new RiskFactor("r1", "Risk", null, List.of("m1"), "reason");
        RiskEvaluation evaluation = new RiskEvaluation("r1", 60, List.of("advice"), "reason");
        when(dataRetrievalService.getRisk("r1")).thenReturn(risk);
        when(judgementService.evaluate(child, risk)).thenReturn(evaluation);

        assertEquals(evaluation, officerService.getRisk(officer, "child-1", "r1"));
        verify(judgementService).evaluate(child, risk);
    }

    @Test
    void getMeasurementsDelegatesWithTheChildsDocument() {
        givenChildIsFindable();
        List<MeasurementSet> history = List.of(
                new MeasurementSet("m1", "child-1", Instant.now(), List.of(5200.0), null));
        when(userDataService.getMeasurements(child, "m1")).thenReturn(history);

        assertEquals(history, officerService.getMeasurements(officer, "child-1", "m1"));
        verify(userDataService).getMeasurements(child, "m1");
    }

    @Test
    void setScoreDelegatesToTheChild() {
        givenChildIsFindable();

        officerService.setScore(officer, "child-1", "oct", 1, 2);

        verify(userDataService).setScore(officer, child, "oct", 1, 2);
    }

    @Test
    void getOverdueTagsEachChildsOverdueMeasurements() {
        officer.setStatus(org.vnk.healthmonitor.domain.dto.UserStatus.MEDICAL_OFFICER);
        when(userData.findByOfficerId("officer-1")).thenReturn(List.of(child));
        when(userReminderService.getOverdue(child)).thenReturn(List.of(
                new Reminder("m1", Instant.parse("2026-01-09T00:00:00Z")),
                new Reminder("m2", Instant.parse("2026-01-08T00:00:00Z"))));

        List<ChildReminder> overdue = officerService.getOverdue(officer);

        assertEquals(2, overdue.size());
        // Ordered by due date across children.
        assertEquals("m2", overdue.get(0).measurementId());
        assertEquals("child-1", overdue.get(0).childId());
        assertEquals(Instant.parse("2026-01-08T00:00:00Z"), overdue.get(0).dueDate());
    }

    @Test
    void onlyMedicalOfficersCanListOverdue() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> officerService.getOverdue(officer));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    @Test
    void getUpcomingDelegatesWithTheChildsDocument() {
        givenChildIsFindable();
        List<Reminder> reminders = List.of(
                new Reminder("m1", Instant.parse("2026-01-12T00:00:00Z")));
        when(userReminderService.getUpcoming(child, 7)).thenReturn(reminders);

        assertEquals(reminders, officerService.getUpcoming(officer, "child-1", 7));
        verify(userReminderService).getUpcoming(child, 7);
    }
}
