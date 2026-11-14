package org.vnk.healthmonitor.controller;

import org.vnk.healthmonitor.domain.dto.Measurement;
import org.vnk.healthmonitor.domain.dto.RiskFactor;
import org.vnk.healthmonitor.service.DataRetrievalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Serves measurement instructions as well as risk overviews.
 */
@RestController
public class DataController {

    private final DataRetrievalService dataRetrievalService;

    public DataController(DataRetrievalService dataRetrievalService) {
        this.dataRetrievalService = dataRetrievalService;
    }

    /**
     * Returns a measurement's instructions.
     */
    @GetMapping("/data/measurements/{measurementID}")
    public Measurement getMeasurement(@PathVariable("measurementID") String measurementId) {
        return dataRetrievalService.getMeasurement(measurementId);
    }

    /**
     * Returns a risk's children measurements and its justification.
     */
    @GetMapping("/data/risks/{riskID}")
    public RiskFactor getRisk(@PathVariable("riskID") String riskId) {
        return dataRetrievalService.getRisk(riskId);
    }

    /**
     * Returns all risk factors, so the frontend home grid hardcodes nothing.
     */
    @GetMapping("/data/risks")
    public List<RiskFactor> getRisks() {
        return dataRetrievalService.getRisks();
    }
}
