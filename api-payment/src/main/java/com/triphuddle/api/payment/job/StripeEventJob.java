package com.triphuddle.api.payment.job;

import com.triphuddle.api.payment.service.StripeEventProcessingException;
import com.triphuddle.api.payment.service.StripeEventProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Scheduled job that hands stored Stripe events to their handlers, one transaction per event.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StripeEventJob {

    // Bounds one run; whatever is left waits for the next.
    private static final int MAX_EVENTS_PER_RUN = 200;

    private final StripeEventProcessor stripeEventProcessor;

    /**
     * Process due events until none is left or the run's bound is reached.
     */
    @Scheduled(fixedDelayString = "${app.payment.event-interval-ms}",
            initialDelayString = "${app.payment.event-interval-ms}")
    public void processDue() {
        Instant now = Instant.now();
        int total = 0;
        for (int i = 0; i < MAX_EVENTS_PER_RUN; i++) {
            Optional<UUID> handled;
            try {
                handled = stripeEventProcessor.processNext(now);
            } catch (StripeEventProcessingException e) {
                stripeEventProcessor.recordFailure(e.getEventId(), e.getCause(), now);
                continue;
            } catch (RuntimeException e) {
                log.error("Stripe event run failed [processed={}]", total, e);
                break;
            }
            if (handled.isEmpty()) {
                break;
            }
            total++;
        }
        if (total > 0) {
            log.info("Stripe events handled [count={}]", total);
        }
    }
}
