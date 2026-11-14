package org.vnk.healthmonitor.functional;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The static data endpoints: the frontend hardcodes nothing, so these
 * responses must be complete and correctly merged from the JSON files.
 */
class DataEndpointsFunctionalTest extends FunctionalTestBase {

    @Test
    void allRisksAreServedWithFullShape() throws Exception {
        HttpResponse<String> response = send("GET", "/data/risks");
        assertEquals(200, response.statusCode());

        List<RiskDto> risks = parseList(response.body(), RiskDto.class);
        assertTrue(risks.size() >= 2, "Expected the real risk set");
        for (RiskDto risk : risks) {
            assertNotNull(risk.id());
            assertNotNull(risk.name());
            assertNotNull(risk.category());
            assertNotNull(risk.measurementIds());
            assertNotNull(risk.reason());
        }
        assertTrue(risks.stream().anyMatch(r -> r.category().equals("physical")));
        assertTrue(risks.stream().anyMatch(r -> r.category().equals("psychological")));
    }

    @Test
    void riskDetailContainsChildMeasurementsAndJustification() throws Exception {
        HttpResponse<String> response = send("GET", "/data/risks/immune_dysregulation");
        assertEquals(200, response.statusCode());

        RiskDto risk = parse(response.body(), RiskDto.class);
        assertTrue(risk.measurementIds().contains("white_blood_cells"));
        assertNotNull(risk.reason());
    }

    @Test
    void measurementMergesDetailsAndInstructions() throws Exception {
        HttpResponse<String> response = send("GET", "/data/measurements/white_blood_cells");
        assertEquals(200, response.statusCode());

        MeasurementDto measurement = parse(response.body(), MeasurementDto.class);
        assertEquals("numerical", measurement.category());
        assertNotNull(measurement.unit());
        assertEquals(4500.0, measurement.abnormalLow());
        assertEquals(11000.0, measurement.abnormalHigh());
        assertNotNull(measurement.instructions());
        assertTrue(measurement.instructions().size() > 0);
        assertNotNull(measurement.instructions().get(0).text());
    }

    @Test
    void visualMeasurementHasCategoryVisualAndNoThresholds() throws Exception {
        HttpResponse<String> response = send("GET", "/data/measurements/oct_fundus_scan");
        assertEquals(200, response.statusCode());

        MeasurementDto measurement = parse(response.body(), MeasurementDto.class);
        assertEquals("visual", measurement.category());
        assertNull(measurement.abnormalHigh());
    }
}
