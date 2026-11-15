package org.vnk.healthmonitor.domain.dto;

/**
 * The guideline bounds for one measurement in one environment. Every
 * bound is optional: a null side does not apply (e.g. a measurement
 * with no dangerousHigh never reaches the dangerous-high band).
 */
public record Range(
        Environment situation,
        Double dangerousLow,
        Double abnormalLow,
        Double abnormalHigh,
        Double dangerousHigh
) {
}
