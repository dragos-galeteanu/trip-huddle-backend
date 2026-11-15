package com.triphuddle.api.payment.job;

import com.triphuddle.api.payment.config.PaymentProperties;
import com.triphuddle.api.payment.service.ReconciliationException;
import com.triphuddle.api.payment.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Scheduled job that compares paid and pending subscriptions with Stripe, each about once per
 * {@code app.payment.reconcile-every-hours}, one transaction per subscription.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationJob {

    // Bounds one run, so a large backlog is spread over several runs and Stripe's rate limit.
    private static final int MAX_SUBSCRIPTIONS_PER_RUN = 100;

    private final ReconciliationService reconciliationService;
    private final PaymentProperties paymentProperties;

    /**
     * Compare due subscriptions until none is left or the run's bound is reached.
     */
    @Scheduled(fixedDelayString = "${app.payment.reconcile-interval-ms}",
            initialDelayString = "${app.payment.reconcile-interval-ms}")
    public void reconcileDue() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofHours(paymentProperties.reconcileEveryHours()));
        int total = 0;
        for (int i = 0; i < MAX_SUBSCRIPTIONS_PER_RUN; i++) {
            Optional<UUID> reconciled;
            try {
                reconciled = reconciliationService.reconcileNext(now, cutoff);
            } catch (ReconciliationException e) {
                reconciliationService.recordFailure(e.getLicenseSubscriptionId(), e.getCause(), now);
                continue;
            } catch (RuntimeException e) {
                log.error("Reconciliation run failed [reconciled={}]", total, e);
                break;
            }
            if (reconciled.isEmpty()) {
                break;
            }
            total++;
        }
        if (total > 0) {
            log.info("Subscriptions reconciled with Stripe [count={}]", total);
        }
    }
}
