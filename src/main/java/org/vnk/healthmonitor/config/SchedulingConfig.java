package org.vnk.healthmonitor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the scheduler that powers the daily/weekly/monthly
 * measurement reminder notifications.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
