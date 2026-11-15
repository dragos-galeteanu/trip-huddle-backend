package com.triphuddle.api.license.job;

import com.triphuddle.api.license.config.LicenseProperties;
import com.triphuddle.api.license.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Scheduled job that reminds of auto-renewals coming up within {@code app.license.renewal-reminder-days}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RenewalReminderJob {

    private static final int BATCH_SIZE = 50;

    // Bounds one run; whatever is left waits for the next.
    private static final int MAX_BATCHES_PER_RUN = 20;

    private final SubscriptionService subscriptionService;
    private final LicenseProperties licenseProperties;

    /**
     * Send the due reminders in batches, each in its own transaction.
     */
    @Scheduled(fixedDelayString = "${app.license.renewal-reminder-interval-ms}",
            initialDelayString = "${app.license.renewal-reminder-interval-ms}")
    public void remind() {
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofDays(licenseProperties.renewalReminderDays()));
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int reminded;
            try {
                reminded = subscriptionService.remindDue(now, until, BATCH_SIZE);
            } catch (RuntimeException e) {
                log.error("Renewal reminder batch failed [batch={}]", batch + 1, e);
                break;
            }
            total += reminded;
            if (reminded < BATCH_SIZE) {
                break;
            }
        }
        if (total > 0) {
            log.info("Renewal reminders sent [count={}, until={}]", total, until);
        }
    }
}
