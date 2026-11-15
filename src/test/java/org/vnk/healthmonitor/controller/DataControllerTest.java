package org.vnk.healthmonitor.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.vnk.healthmonitor.domain.dto.RiskFactor;
import org.vnk.healthmonitor.service.DataRetrievalService;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for DataController: routes, status codes, and JSON shape.
 * The service is mocked, so only the controller is exercised.
 */
class DataControllerTest {

    private DataRetrievalService dataRetrievalService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        dataRetrievalService = mock(DataRetrievalService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DataController(dataRetrievalService)).build();
    }

    @Test
    void getAllRisksReturnsArray() throws Exception {
        when(dataRetrievalService.getRisks()).thenReturn(List.of(
                new RiskFactor("r1", "Risk One", null, List.of("m1"), "reason one"),
                new RiskFactor("r2", "Risk Two", null, List.of(), "reason two")));

        mockMvc.perform(get("/data/risks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value("r1"))
                .andExpect(jsonPath("$[0].name").value("Risk One"))
                .andExpect(jsonPath("$[1].reason").value("reason two"));
    }

    @Test
    void getRiskReturnsRiskFactor() throws Exception {
        when(dataRetrievalService.getRisk("r1")).thenReturn(
                new RiskFactor("r1", "Risk One", null, List.of("m1", "m2"), "reason one"));

        mockMvc.perform(get("/data/risks/r1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("r1"))
                .andExpect(jsonPath("$.measurementIds[1]").value("m2"));
    }

    @Test
    void unknownRiskReturns404() throws Exception {
        when(dataRetrievalService.getRisk("nope")).thenThrow(
                new ResponseStatusException(NOT_FOUND, "Unknown risk: nope"));

        String body = mockMvc.perform(get("/data/risks/nope"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertEquals("", body);
    }

    @Test
    void getFlagsReturnsTheVisualMeasurementFlags() throws Exception {
        when(dataRetrievalService.getFlags("m2")).thenReturn(List.of("Cotton wool spots", "Vessel tortuosity"));

        mockMvc.perform(get("/data/measurements/m2/flags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0]").value("Cotton wool spots"))
                .andExpect(jsonPath("$[1]").value("Vessel tortuosity"));
    }

    @Test
    void flagsForANumericalMeasurementReturn400() throws Exception {
        when(dataRetrievalService.getFlags("m1")).thenThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Measurement m1 is not a visual measurement"));

        mockMvc.perform(get("/data/measurements/m1/flags"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void flagsForAnUnknownMeasurementReturn404() throws Exception {
        when(dataRetrievalService.getFlags("nope")).thenThrow(
                new ResponseStatusException(NOT_FOUND, "Unknown measurement: nope"));

        mockMvc.perform(get("/data/measurements/nope/flags"))
                .andExpect(status().isNotFound());
    }
}
