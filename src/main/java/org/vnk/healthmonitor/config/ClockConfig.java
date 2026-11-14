package org.vnk.healthmonitor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides the application Clock. Services inject it instead of calling
 * Instant.now() directly, so date math (reminders, measurement timestamps)
 * is deterministic and unit-testable with a fixed Clock.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
