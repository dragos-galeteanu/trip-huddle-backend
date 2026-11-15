package com.triphuddle.api.license.job;

import com.triphuddle.api.license.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Scheduled job that renews active subscriptions at the end of their period, or expires them if they don't renew.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionRenewalJob {

    private static final int BATCH_SIZE = 50;

    // Bounds one run; whatever is left waits for the next.
    private static final int MAX_BATCHES_PER_RUN = 20;

    private final SubscriptionService subscriptionService;

    /**
     * Renew or expire the due subscriptions in batches, each in its own transaction.
     */
    @Scheduled(fixedDelayString = "${app.license.renewal-interval-ms}",
            initialDelayString = "${app.license.renewal-interval-ms}")
    public void renewOrExpire() {
        Instant now = Instant.now();
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int handled;
            try {
                handled = subscriptionService.renewOrExpireDue(now, BATCH_SIZE);
            } catch (RuntimeException e) {
                log.error("Subscription renewal batch failed [batch={}]", batch + 1, e);
                break;
            }
            total += handled;
            if (handled < BATCH_SIZE) {
                break;
            }
        }
        if (total > 0) {
            log.info("Subscriptions renewed or expired [count={}]", total);
        }
    }
}
