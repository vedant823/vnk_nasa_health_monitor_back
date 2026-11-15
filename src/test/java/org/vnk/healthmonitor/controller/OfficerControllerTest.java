package org.vnk.healthmonitor.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.vnk.healthmonitor.domain.document.MeasurementSet;
import org.vnk.healthmonitor.domain.document.UserDocument;
import org.vnk.healthmonitor.domain.dto.ChildOverview;
import org.vnk.healthmonitor.domain.dto.ChildReminder;
import org.vnk.healthmonitor.service.OfficerService;
import org.vnk.healthmonitor.service.UserLoginService;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for OfficerController: routes, status codes, and that the
 * Authorization header is resolved and delegated correctly. The service
 * is mocked, so only the controller is exercised.
 */
class OfficerControllerTest {

    private static final String AUTH = "Authorization";
    private static final String BEARER = "Bearer officer-token";

    private UserLoginService userLoginService;
    private OfficerService officerService;
    private UserDocument officer;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userLoginService = mock(UserLoginService.class);
        officerService = mock(OfficerService.class);
        officer = new UserDocument("cmo", "hash", "officer-token");
        when(userLoginService.resolveUser(BEARER)).thenReturn(officer);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new OfficerController(userLoginService, officerService)).build();
    }

    @Test
    void getChildrenReturnsTheOverviews() throws Exception {
        when(officerService.listChildren(officer)).thenReturn(List.of(
                new ChildOverview("c1", "kid", Instant.parse("2026-01-10T12:00:00Z"))));

        mockMvc.perform(get("/users/children").header(AUTH, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId").value("c1"))
                .andExpect(jsonPath("$[0].username").value("kid"))
                .andExpect(jsonPath("$[0].latestMeasurement").value("2026-01-10T12:00:00Z"));

        verify(userLoginService).resolveUser(BEARER);
    }

    @Test
    void getChildRisksDelegates() throws Exception {
        mockMvc.perform(get("/users/children/c1/risks").header(AUTH, BEARER))
                .andExpect(status().isOk());

        verify(officerService).getRisks(officer, "c1");
    }

    @Test
    void getChildRiskDelegates() throws Exception {
        mockMvc.perform(get("/users/children/c1/risks/r1").header(AUTH, BEARER))
                .andExpect(status().isOk());

        verify(officerService).getRisk(officer, "c1", "r1");
    }

    @Test
    void getChildMeasurementsDelegates() throws Exception {
        when(officerService.getMeasurements(officer, "c1", "oct")).thenReturn(List.of(
                new MeasurementSet("oct", "c1", Instant.parse("2026-01-10T00:00:00Z"),
                        null, List.of("img"), 2)));

        mockMvc.perform(get("/users/children/c1/measurements/oct").header(AUTH, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].numAbnormal").value(2));

        verify(officerService).getMeasurements(officer, "c1", "oct");
    }

    @Test
    void setChildScoreDelegatesWithoutIndex() throws Exception {
        mockMvc.perform(put("/users/children/c1/measurements/oct/score")
                        .header(AUTH, BEARER)
                        .param("numAbnormal", "2"))
                .andExpect(status().isOk());

        verify(officerService).setScore(officer, "c1", "oct", null, 2);
    }

    @Test
    void setChildScoreDelegatesWithIndex() throws Exception {
        mockMvc.perform(put("/users/children/c1/measurements/oct/score")
                        .header(AUTH, BEARER)
                        .param("numAbnormal", "0")
                        .param("measurementIndex", "1"))
                .andExpect(status().isOk());

        verify(officerService).setScore(officer, "c1", "oct", 1, 0);
    }

    @Test
    void getOverdueDelegatesWithOverdueEntries() throws Exception {
        when(officerService.getOverdue(officer)).thenReturn(List.of(
                new ChildReminder("c1", "m1", Instant.parse("2026-01-09T00:00:00Z"))));

        mockMvc.perform(get("/users/overdue").header(AUTH, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].childId").value("c1"))
                .andExpect(jsonPath("$[0].measurementId").value("m1"))
                .andExpect(jsonPath("$[0].dueDate").value("2026-01-09T00:00:00Z"));

        verify(officerService).getOverdue(officer);
    }

    @Test
    void getChildUpcomingDefaultsTo30Days() throws Exception {
        mockMvc.perform(get("/users/children/c1/upcoming").header(AUTH, BEARER))
                .andExpect(status().isOk());

        verify(officerService).getUpcoming(officer, "c1", 30);
    }

    @Test
    void getChildUpcomingPassesWithinDays() throws Exception {
        mockMvc.perform(get("/users/children/c1/upcoming")
                        .header(AUTH, BEARER)
                        .param("withinDays", "7"))
                .andExpect(status().isOk());

        verify(officerService).getUpcoming(officer, "c1", 7);
    }
}
