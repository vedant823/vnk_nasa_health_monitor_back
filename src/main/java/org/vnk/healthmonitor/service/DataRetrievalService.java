package org.vnk.healthmonitor.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.vnk.healthmonitor.domain.dto.BaselineMode;
import org.vnk.healthmonitor.domain.dto.Badge;
import org.vnk.healthmonitor.domain.dto.Criterion;
import org.vnk.healthmonitor.domain.dto.Environment;
import org.vnk.healthmonitor.domain.dto.Instruction;
import org.vnk.healthmonitor.domain.dto.Measurement;
import org.vnk.healthmonitor.domain.dto.MeasurementCategory;
import org.vnk.healthmonitor.domain.dto.Range;
import org.vnk.healthmonitor.domain.dto.RiskFactor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gets the risk data and measurement instructions by parsing the JSON
 * files under the data directory with Jackson into records.
 *
 * Everything is loaded once at startup and fail-fast: malformed files or
 * a risk referencing an unknown measurement ID abort the application.
 */
@Service
public class DataRetrievalService {

    private record RiskFile(List<RiskFactor> risks) {
    }

    private record InstructionsFile(List<RawInstructions> measurements) {
    }

    private record RawInstructions(String id, List<Instruction> instructions) {
    }

    private record DetailsFile(List<RawMeasurement> measurements) {
    }

    private record BadgeFile(List<Badge> badges) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawMeasurement(
            String id,
            String name,
            String type,
            String unit,
            List<RawRange> ranges,
            Boolean canBeNegative,
            String baselineMode,
            String lowRecommendation,
            String highRecommendation) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawRange(
            String situation,
            Double dangerousLow,
            Double abnormalLow,
            Double abnormalHigh,
            Double dangerousHigh) {
    }

    private final Map<String, Measurement> measurements = new HashMap<>();
    private final Map<String, RiskFactor> risks = new LinkedHashMap<>();
    private final List<Badge> badges = new ArrayList<>();

    public DataRetrievalService(@Value("${app.data-dir}") String dataDir) {
        ObjectMapper mapper = new ObjectMapper();
        loadRisks(mapper, dataDir);
        loadMeasurements(mapper, dataDir);
        loadBadges(mapper, dataDir);
    }

    private void loadRisks(ObjectMapper mapper, String dataDir) {
        try {
            RiskFile file = mapper.readValue(new File(dataDir, "riskInfo.json"), RiskFile.class);
            for (RiskFactor risk : file.risks()) {
                risks.put(risk.id(), risk);
            }
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to load riskInfo.json", e);
        }
    }

    private void loadMeasurements(ObjectMapper mapper, String dataDir) {
        Map<String, List<Instruction>> instructionsById = new HashMap<>();
        try {
            InstructionsFile file = mapper.readValue(
                    new File(dataDir, "measurementInstructions.json"), InstructionsFile.class);
            for (RawInstructions entry : file.measurements()) {
                instructionsById.put(entry.id(), entry.instructions());
            }
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to load measurementInstructions.json", e);
        }

        try {
            DetailsFile file = mapper.readValue(
                    new File(dataDir, "measurementDetails.json"), DetailsFile.class);
            for (RawMeasurement raw : file.measurements()) {
                measurements.put(raw.id(), new Measurement(
                        raw.id(),
                        raw.name(),
                        MeasurementCategory.from(raw.type()),
                        raw.unit(),
                        rangesOf(raw.id(), raw.ranges()),
                        raw.canBeNegative() != null && raw.canBeNegative(),
                        raw.baselineMode() == null
                                ? BaselineMode.NONE
                                : BaselineMode.from(raw.baselineMode()),
                        raw.lowRecommendation(),
                        raw.highRecommendation(),
                        instructionsById.get(raw.id())));
            }
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to load measurementDetails.json", e);
        }

        for (RiskFactor risk : risks.values()) {
            for (String measurementId : risk.measurementIds()) {
                if (!measurements.containsKey(measurementId)) {
                    throw new IllegalStateException("Risk " + risk.id()
                            + " references unknown measurement " + measurementId);
                }
            }
        }
    }

    /**
     * Loads badges.json and validates every criterion's measurement IDs
     * against the loaded measurements, so a typo in the JSON aborts the
     * application at startup instead of silently disabling a badge.
     */
    private void loadBadges(ObjectMapper mapper, String dataDir) {
        try {
            BadgeFile file = mapper.readValue(new File(dataDir, "badges.json"), BadgeFile.class);
            badges.addAll(file.badges());
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to load badges.json", e);
        }
        for (Badge badge : badges) {
            if (badge.title() == null || badge.title().isBlank()) {
                throw new IllegalStateException("Badge without a title in badges.json");
            }
            if (badge.description() == null || badge.description().isBlank()) {
                throw new IllegalStateException("Badge " + badge.title() + " has no description");
            }
            for (Criterion criterion : badge.criteria()) {
                if (criterion.count() < 1) {
                    throw new IllegalStateException("Badge " + badge.title()
                            + " has a criterion with count < 1");
                }
                for (String measurementId : criterion.measurementType()) {
                    if (!measurements.containsKey(measurementId)) {
                        throw new IllegalStateException("Badge " + badge.title()
                                + " references unknown measurement " + measurementId);
                    }
                }
            }
        }
    }

    /**
     * Converts one measurement's raw range entries into validated Range
     * records. Every measurement must define exactly one range per
     * environment — analysis always has bounds to work with, and a
     * typo'd or missing situation aborts the application at startup.
     */
    private List<Range> rangesOf(String measurementId, List<RawRange> rawRanges) {
        List<Range> ranges = new ArrayList<>();
        for (RawRange raw : rawRanges) {
            Environment situation;
            try {
                situation = Environment.from(raw.situation());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Measurement " + measurementId
                        + " references unknown environment " + raw.situation(), e);
            }
            if (ranges.stream().anyMatch(r -> r.situation() == situation)) {
                throw new IllegalStateException("Measurement " + measurementId
                        + " has duplicate ranges for environment " + raw.situation());
            }
            ranges.add(new Range(situation, raw.dangerousLow(), raw.abnormalLow(),
                    raw.abnormalHigh(), raw.dangerousHigh()));
        }
        for (Environment environment : Environment.values()) {
            boolean covered = ranges.stream().anyMatch(r -> r.situation() == environment);
            if (!covered) {
                throw new IllegalStateException("Measurement " + measurementId
                        + " has no range for environment " + environment.getLabel());
            }
        }
        return List.copyOf(ranges);
    }

    /**
     * Returns a measurement's details and instructions.
     */
    public Measurement getMeasurement(String measurementId) {
        Measurement measurement = measurements.get(measurementId);
        if (measurement == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown measurement: " + measurementId);
        }
        return measurement;
    }

    /**
     * Returns a risk's child measurements and its justification.
     */
    public RiskFactor getRisk(String riskId) {
        RiskFactor risk = risks.get(riskId);
        if (risk == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown risk: " + riskId);
        }
        return risk;
    }

    /**
     * Returns all risk factors.
     */
    public List<RiskFactor> getRisks() {
        return List.copyOf(risks.values());
    }

    /**
     * Returns the IDs of all known measurements.
     */
    public List<String> getMeasurementIds() {
        return List.copyOf(measurements.keySet());
    }

    /**
     * Returns every badge definition from badges.json, in file order.
     */
    public List<Badge> getBadges() {
        return List.copyOf(badges);
    }
}
