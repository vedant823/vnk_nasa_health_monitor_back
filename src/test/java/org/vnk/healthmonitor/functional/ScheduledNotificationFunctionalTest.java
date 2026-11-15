package org.vnk.healthmonitor.functional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.vnk.healthmonitor.service.NotificationSender;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that Spring's @Scheduled trigger actually fires and delivers
 * digests through the NotificationSender — the unit tests cover the
 * digest logic and the send loop, but not the scheduling wiring itself.
 *
 * The app boots with a one-second cron and a counting sender replacing
 * the logging sender, so the test can observe real scheduled deliveries.
 */
@TestPropertySource(properties = "app.notifications.cron=0/1 * * * * *")
class ScheduledNotificationFunctionalTest extends FunctionalTestBase {

    /** Counts every digest the scheduler delivers. */
    static final AtomicInteger SENT = new AtomicInteger();

    @TestConfiguration
    static class CountingSenderConfig {
        @Bean
        @Primary
        NotificationSender countingNotificationSender() {
            return digest -> SENT.incrementAndGet();
        }
    }

    @Test
    void scheduledTriggerDeliversDigestsWithoutWaitingForTheRealCron() throws Exception {
        // Make sure at least one user exists to receive a digest.
        createAccount(uniqueUsername());

        // The cron fires every second; wait up to 15 seconds for the
        // scheduler to deliver at least one digest.
        long deadline = System.currentTimeMillis() + 15_000;
        while (SENT.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(250);
        }

        assertTrue(SENT.get() >= 1,
                "The @Scheduled trigger never delivered a digest within 15 seconds");
    }
}
