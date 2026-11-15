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
 * Scheduled job that ends subscriptions left waiting for payment longer than {@code app.license.pending-expiry-hours}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingSubscriptionTimeoutJob {

    private static final int BATCH_SIZE = 50;

    // Bounds one run; whatever is left waits for the next.
    private static final int MAX_BATCHES_PER_RUN = 20;

    private final SubscriptionService subscriptionService;
    private final LicenseProperties licenseProperties;

    /**
     * End the stale pending subscriptions in batches, each in its own transaction.
     */
    @Scheduled(fixedDelayString = "${app.license.pending-timeout-interval-ms}",
            initialDelayString = "${app.license.pending-timeout-interval-ms}")
    public void endStalePending() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(licenseProperties.pendingExpiryHours()));
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int ended;
            try {
                ended = subscriptionService.endStalePending(cutoff, BATCH_SIZE);
            } catch (RuntimeException e) {
                log.error("Pending subscription timeout batch failed [batch={}]", batch + 1, e);
                break;
            }
            total += ended;
            if (ended < BATCH_SIZE) {
                break;
            }
        }
        if (total > 0) {
            log.info("Pending subscriptions timed out [count={}, cutoff={}]", total, cutoff);
        }
    }
}
