package org.vnk.healthmonitor.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.vnk.healthmonitor.domain.dto.BaselineMode;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for loading and serving the JSON data files, using small
 * fixture copies under src/test/resources/data (and data-bad for the
 * fail-fast case).
 */
class DataRetrievalServiceTest {

    private DataRetrievalService serviceFor(String directory) {
        return new DataRetrievalService(Path.of("src", "test", "resources", directory).toString());
    }

    @Test
    void loadsMeasurementsMergedWithInstructions() {
        DataRetrievalService service = serviceFor("data");

        var measurement = service.getMeasurement("m1");

        assertEquals("Test Numerical", measurement.name());
        assertEquals("numerical", measurement.category().getLabel());
        assertEquals(4500.0, measurement.abnormalLow());
        assertEquals(2, measurement.instructions().size());
        assertEquals("First step.", measurement.instructions().get(0).text());
    }

    @Test
    void visualMeasurementHasNoThresholds() {
        DataRetrievalService service = serviceFor("data");

        var measurement = service.getMeasurement("m2");

        assertEquals("visual", measurement.category().getLabel());
        assertNull(measurement.dangerousLow());
        assertEquals(1, measurement.instructions().size());
    }

    @Test
    void missingOptionalBoundsStayNull() {
        DataRetrievalService service = serviceFor("data");

        var measurement = service.getMeasurement("m3");

        assertNull(measurement.dangerousLow());
        assertNull(measurement.abnormalLow());
        assertNull(measurement.dangerousHigh());
        assertEquals(100.0, measurement.abnormalHigh());
    }

    @Test
    void unknownMeasurementThrows404() {
        DataRetrievalService service = serviceFor("data");

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.getMeasurement("nope"));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void loadsAllRisksInFileOrder() {
        DataRetrievalService service = serviceFor("data");

        var risks = service.getRisks();

        assertEquals(2, risks.size());
        assertEquals("r1", risks.get(0).id());
        assertEquals("physical", risks.get(0).category().getLabel());
        assertEquals(List.of("m1", "m2"), risks.get(0).measurementIds());
        assertEquals("r2", risks.get(1).id());
        assertTrue(risks.get(1).measurementIds().isEmpty());
    }

    @Test
    void unknownRiskThrows404() {
        DataRetrievalService service = serviceFor("data");

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.getRisk("nope"));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void listsAllMeasurementIds() {
        DataRetrievalService service = serviceFor("data");

        assertEquals(List.of("m1", "m2", "m3", "m4", "m5"), service.getMeasurementIds());
    }

    @Test
    void baselineModeDefaultsToNoneAndParsesValues() {
        DataRetrievalService service = serviceFor("data");

        assertEquals(BaselineMode.NONE, service.getMeasurement("m1").baselineMode());
        assertEquals(BaselineMode.PERCENT, service.getMeasurement("m4").baselineMode());
        assertEquals(BaselineMode.DIFFERENCE, service.getMeasurement("m5").baselineMode());
    }

    @Test
    void riskReferencingUnknownMeasurementFailsFast() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> serviceFor("data-bad"));

        assertTrue(e.getMessage().contains("unknown measurement unknown_measurement"),
                "Unexpected message: " + e.getMessage());
    }

    @Test
    void loadsBadgesInFileOrder() {
        DataRetrievalService service = serviceFor("data");

        var badges = service.getBadges();

        assertEquals(7, badges.size());
        assertEquals("One Reading", badges.get(0).title());
        assertEquals(1, badges.get(0).criteria().size());
        var streakCriterion = badges.get(2).criteria().get(0);
        assertTrue(streakCriterion.streak());
        assertTrue(streakCriterion.distinctDays());
        assertEquals(3, streakCriterion.count());
    }

    @Test
    void badgeReferencingUnknownMeasurementFailsFast() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> serviceFor("data-bad-badges"));

        assertTrue(e.getMessage().contains("unknown measurement unknown_measurement"),
                "Unexpected message: " + e.getMessage());
    }

    @Test
    void badgeWithInvalidCountFailsFast() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> serviceFor("data-bad-count"));

        assertTrue(e.getMessage().contains("count < 1"),
                "Unexpected message: " + e.getMessage());
    }
}
