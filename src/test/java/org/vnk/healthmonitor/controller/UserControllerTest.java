package org.vnk.healthmonitor.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.vnk.healthmonitor.domain.document.MeasurementSet;
import org.vnk.healthmonitor.domain.document.UserDocument;
import org.vnk.healthmonitor.service.DataRetrievalService;
import org.vnk.healthmonitor.service.BadgeService;
import org.vnk.healthmonitor.service.JudgementService;
import org.vnk.healthmonitor.service.NotificationScheduler;
import org.vnk.healthmonitor.service.UserDataService;
import org.vnk.healthmonitor.service.UserLoginService;
import org.vnk.healthmonitor.service.UserReminderService;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for UserController: routes, status codes, and that the
 * Authorization header is resolved and delegated correctly. All services
 * are mocked, so only the controller is exercised.
 */
class UserControllerTest {

    private static final String AUTH = "Authorization";
    private static final String BEARER = "Bearer token-123";

    private UserDataService userDataService;
    private UserLoginService userLoginService;
    private UserReminderService userReminderService;
    private JudgementService judgementService;
    private DataRetrievalService dataRetrievalService;
    private NotificationScheduler notificationScheduler;
    private BadgeService badgeService;
    private UserDocument user;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userDataService = mock(UserDataService.class);
        userLoginService = mock(UserLoginService.class);
        userReminderService = mock(UserReminderService.class);
        judgementService = mock(JudgementService.class);
        dataRetrievalService = mock(DataRetrievalService.class);
        notificationScheduler = mock(NotificationScheduler.class);
        badgeService = mock(BadgeService.class);
        user = new UserDocument("alice", "hash", "token-123");
        when(userLoginService.resolveUser(BEARER)).thenReturn(user);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(
                userDataService, userLoginService, userReminderService,
                judgementService, dataRetrievalService, notificationScheduler,
                badgeService)).build();
    }

    @Test
    void getMeasurementsReturnsHistory() throws Exception {
        List<MeasurementSet> history = List.of(new MeasurementSet(
                "m1", "user", Instant.parse("2026-01-10T00:00:00Z"), List.of(5200.0), null));
        when(userDataService.getMeasurements(user, "m1")).thenReturn(history);

        mockMvc.perform(get("/users/measurements/m1").header(AUTH, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].measurementId").value("m1"))
                .andExpect(jsonPath("$[0].values[0]").value(5200.0));

        verify(userLoginService).resolveUser(BEARER);
    }

    @Test
    void logMeasurementDelegatesWithBody() throws Exception {
        mockMvc.perform(post("/users/measurements/m1")
                        .header(AUTH, BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"values\":[5200.0]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<org.vnk.healthmonitor.domain.dto.LogMeasurementRequest> captor =
                ArgumentCaptor.forClass(org.vnk.healthmonitor.domain.dto.LogMeasurementRequest.class);
        verify(userDataService).logMeasurement(org.mockito.ArgumentMatchers.eq(user),
                eq("m1"), captor.capture());
        assertEquals(List.of(5200.0), captor.getValue().values());
    }

    @Test
    void deleteMeasurementDelegatesWithoutIndex() throws Exception {
        mockMvc.perform(delete("/users/measurements/m1").header(AUTH, BEARER))
                .andExpect(status().isOk());

        verify(userDataService).deleteMeasurement(user, "m1", null);
    }

    @Test
    void deleteMeasurementDelegatesWithIndex() throws Exception {
        mockMvc.perform(delete("/users/measurements/m1")
                        .header(AUTH, BEARER)
                        .param("measurementIndex", "2"))
                .andExpect(status().isOk());

        verify(userDataService).deleteMeasurement(user, "m1", 2);
    }

    @Test
    void setScoreDelegatesWithoutIndex() throws Exception {
        mockMvc.perform(put("/users/measurements/m1/score")
                        .header(AUTH, BEARER)
                        .param("numAbnormal", "2"))
                .andExpect(status().isOk());

        verify(userDataService).setScore(user, user, "m1", null, 2);
    }

    @Test
    void setScoreDelegatesWithIndex() throws Exception {
        mockMvc.perform(put("/users/measurements/m1/score")
                        .header(AUTH, BEARER)
                        .param("numAbnormal", "0")
                        .param("measurementIndex", "1"))
                .andExpect(status().isOk());

        verify(userDataService).setScore(user, user, "m1", 1, 0);
    }

    @Test
    void setFrequencyDelegatesWithBody() throws Exception {
        mockMvc.perform(put("/users/measurements/m1/frequency")
                        .header(AUTH, BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frequencyDays\":3}"))
                .andExpect(status().isOk());

        verify(userDataService).setFrequency(user, "m1", 3);
    }

    @Test
    void getUpcomingPassesWithinDaysParameter() throws Exception {
        when(userReminderService.getUpcoming(user, 5)).thenReturn(List.of(
                new org.vnk.healthmonitor.domain.dto.Reminder("m1",
                        Instant.parse("2026-01-12T00:00:00Z"))));

        mockMvc.perform(get("/users/upcoming").header(AUTH, BEARER).param("withinDays", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].measurementId").value("m1"))
                .andExpect(jsonPath("$[0].dueDate").value("2026-01-12T00:00:00Z"));

        verify(userReminderService).getUpcoming(eq(user), eq(5));
    }

    @Test
    void getUpcomingDefaultsTo30Days() throws Exception {
        when(userReminderService.getUpcoming(org.mockito.ArgumentMatchers.any(UserDocument.class), anyInt()))
                .thenReturn(List.of());

        mockMvc.perform(get("/users/upcoming").header(AUTH, BEARER))
                .andExpect(status().isOk());

        verify(userReminderService).getUpcoming(user, 30);
    }

    @Test
    void getRisksReturnsAllEvaluations() throws Exception {
        List<org.vnk.healthmonitor.domain.dto.RiskEvaluation> evaluations = List.of(
                new org.vnk.healthmonitor.domain.dto.RiskEvaluation("r1", 100,
                        List.of("No action needed"), "All measurements are within the normal range."));
        when(judgementService.evaluateAll(user)).thenReturn(evaluations);

        mockMvc.perform(get("/users/risks").header(AUTH, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].riskId").value("r1"))
                .andExpect(jsonPath("$[0].score").value(100));
    }

    @Test
    void getRiskDelegatesWithRiskId() throws Exception {
        org.vnk.healthmonitor.domain.dto.RiskFactor risk = new org.vnk.healthmonitor.domain.dto.RiskFactor(
                "r1", "Risk One", null, List.of("m1"), "reason");
        org.vnk.healthmonitor.domain.dto.RiskEvaluation evaluation =
                new org.vnk.healthmonitor.domain.dto.RiskEvaluation("r1", 60,
                        List.of("advice"), "reason text");
        when(dataRetrievalService.getRisk("r1")).thenReturn(risk);
        when(judgementService.evaluate(user, risk)).thenReturn(evaluation);

        mockMvc.perform(get("/users/risks/r1").header(AUTH, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskId").value("r1"))
                .andExpect(jsonPath("$.score").value(60))
                .andExpect(jsonPath("$.advice[0]").value("advice"));

        verify(judgementService).evaluate(user, risk);
    }

    @Test
    void getTrendDelegatesWithMeasurementId() throws Exception {
        when(userDataService.getTrend(user, "m1")).thenReturn(
                new org.vnk.healthmonitor.domain.dto.MeasurementTrend("m1",
                        org.vnk.healthmonitor.domain.dto.TrendDirection.DOWN));

        mockMvc.perform(get("/users/measurements/m1/trend").header(AUTH, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurementId").value("m1"))
                .andExpect(jsonPath("$.direction").value("down"));

        verify(userDataService).getTrend(user, "m1");
    }

    @Test
    void setOfficerDelegatesWithParameter() throws Exception {
        mockMvc.perform(put("/users/officer")
                        .header(AUTH, BEARER)
                        .param("newOfficer", "cmo"))
                .andExpect(status().isOk());

        verify(userDataService).setOfficer(user, "cmo");
    }

    @Test
    void getOfficerReturnsTheUsername() throws Exception {
        when(userDataService.officerOf(user)).thenReturn("cmo");

        mockMvc.perform(get("/users/officer").header(AUTH, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("cmo"));

        verify(userDataService).officerOf(user);
    }

    @Test
    void setStatusDelegatesWithParameter() throws Exception {
        mockMvc.perform(put("/users/status")
                        .header(AUTH, BEARER)
                        .param("newStatus", "medical officer"))
                .andExpect(status().isOk());

        verify(userDataService).setStatus(user, "medical officer");
    }

    @Test
    void getStatusReturnsTheLabel() throws Exception {
        mockMvc.perform(get("/users/status").header(AUTH, BEARER))
                .andExpect(status().isOk());

        verify(userLoginService).resolveUser(BEARER);
    }

    @Test
    void getEnvironmentDelegates() throws Exception {
        mockMvc.perform(get("/users/environment").header(AUTH, BEARER))
                .andExpect(status().isOk());

        verify(userLoginService).resolveUser(BEARER);
    }

    @Test
    void setEnvironmentDelegatesWithParameter() throws Exception {
        mockMvc.perform(put("/users/environment")
                        .header(AUTH, BEARER)
                        .param("newEnvironment", "mars"))
                .andExpect(status().isOk());

        verify(userDataService).setEnvironment(user, "mars");
    }

    @Test
    void getDigestDelegatesToTheScheduler() throws Exception {
        org.vnk.healthmonitor.domain.dto.ReminderDigest digest =
                new org.vnk.healthmonitor.domain.dto.ReminderDigest(
                        "alice", List.of(), List.of(), List.of());
        when(notificationScheduler.buildDigest(user)).thenReturn(digest);

        mockMvc.perform(get("/users/digest").header(AUTH, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.daily", hasSize(0)))
                .andExpect(jsonPath("$.weekly", hasSize(0)))
                .andExpect(jsonPath("$.monthly", hasSize(0)));

        verify(notificationScheduler).buildDigest(user);
    }

    @Test
    void getBadgesDelegatesToTheBadgeService() throws Exception {
        org.vnk.healthmonitor.domain.dto.Badge badge =
                new org.vnk.healthmonitor.domain.dto.Badge(
                        "First Check-in", "Log any measurement once.", List.of());
        when(badgeService.evaluate(user)).thenReturn(new org.vnk.healthmonitor.domain.dto.BadgeSet(
                List.of(badge),
                List.of(new org.vnk.healthmonitor.domain.dto.BadgeStatus(
                        new org.vnk.healthmonitor.domain.dto.Badge("Week Streak", "Log a measurement every day for a week.", List.of()),
                        List.of(0.0)))));

        mockMvc.perform(get("/users/badges").header(AUTH, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.earned", hasSize(1)))
                .andExpect(jsonPath("$.earned[0].title").value("First Check-in"))
                .andExpect(jsonPath("$.earned[0].description").value("Log any measurement once."))
                .andExpect(jsonPath("$.unearned", hasSize(1)))
                .andExpect(jsonPath("$.unearned[0].badge.title").value("Week Streak"))
                .andExpect(jsonPath("$.unearned[0].badge.description").value("Log a measurement every day for a week."))
                .andExpect(jsonPath("$.unearned[0].progress[0]").value(0.0));

        verify(badgeService).evaluate(user);
    }
}
