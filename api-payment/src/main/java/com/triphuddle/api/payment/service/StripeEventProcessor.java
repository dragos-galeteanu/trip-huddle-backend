package com.triphuddle.api.payment.service;

import com.stripe.model.Event;
import com.triphuddle.api.payment.config.PaymentProperties;
import com.triphuddle.api.payment.entity.StripeEvent;
import com.triphuddle.api.payment.entity.StripeEventStatus;
import com.triphuddle.api.payment.repository.StripeEventRepository;
import com.triphuddle.api.payment.webhook.StripeEventHandler;
import com.triphuddle.stripe.webhook.StripeWebhooks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Works through stored Stripe events one at a time, each in its own transaction, retrying failures with backoff.
 */
@Slf4j
@Service
public class StripeEventProcessor {

    private static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(30);
    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(1);
    private static final int MAX_ERROR_LENGTH = 4000;

    private final StripeEventRepository stripeEventRepository;
    private final StripeWebhooks stripeWebhooks;
    private final PaymentProperties paymentProperties;
    private final Map<String, StripeEventHandler> handlersByType = new HashMap<>();

    public StripeEventProcessor(StripeEventRepository stripeEventRepository, StripeWebhooks stripeWebhooks,
                                PaymentProperties paymentProperties,
                                ObjectProvider<StripeEventHandler> handlers) {
        this.stripeEventRepository = stripeEventRepository;
        this.stripeWebhooks = stripeWebhooks;
        this.paymentProperties = paymentProperties;
        // ObjectProvider rather than List, so the service starts while no handler exists yet.
        for (StripeEventHandler handler : handlers.orderedStream().toList()) {
            for (String type : handler.eventTypes()) {
                StripeEventHandler previous = handlersByType.putIfAbsent(type, handler);
                if (previous != null) {
                    throw new IllegalStateException("Stripe event type %s has two handlers: %s and %s".formatted(
                            type, previous.getClass().getSimpleName(), handler.getClass().getSimpleName()));
                }
            }
        }
        log.info("Stripe event handlers registered [types={}]", handlersByType.keySet());
    }

    /**
     * Handle the oldest due event, if any.
     *
     * @param now the current time
     * @return the ID of the stored event handled, or empty if none was due
     * @throws StripeEventProcessingException if its handler failed; the transaction is rolled back
     */
    @Transactional
    public Optional<UUID> processNext(Instant now) {
        List<StripeEvent> due = stripeEventRepository.lockDue(now, PageRequest.of(0, 1));
        if (due.isEmpty()) {
            return Optional.empty();
        }
        StripeEvent stored = due.get(0);
        StripeEventHandler handler = handlersByType.get(stored.getEventType());
        if (handler == null) {
            finish(stored, StripeEventStatus.IGNORED, now);
            return Optional.of(stored.getId());
        }

        Event event = stripeWebhooks.parse(stored.getPayload());
        if (!stripeWebhooks.isReadable(event)) {
            // Retrying can't help: the endpoint must be moved to the version the code speaks, then
            // the event replayed from Stripe or by staff.
            stored.setLastError("Event is in API version %s, which stripe-java can't read".formatted(
                    event.getApiVersion()));
            finish(stored, StripeEventStatus.FAILED, now);
            log.error("Stripe event in an unreadable API version [stripeEventId={}, apiVersion={}]",
                    stored.getStripeEventId(), event.getApiVersion());
            return Optional.of(stored.getId());
        }

        try {
            handler.handle(event);
        } catch (RuntimeException e) {
            throw new StripeEventProcessingException(stored.getId(), e);
        }
        stored.setLastError(null);
        finish(stored, StripeEventStatus.PROCESSED, now);
        log.info("Stripe event processed [stripeEventId={}, type={}]", stored.getStripeEventId(),
                stored.getEventType());
        return Optional.of(stored.getId());
    }

    /**
     * Record a failed attempt, and schedule the next one or give up.
     *
     * @param eventId the ID of the stored event
     * @param error the failure
     * @param now the current time
     */
    @Transactional
    public void recordFailure(UUID eventId, Throwable error, Instant now) {
        stripeEventRepository.findById(eventId).ifPresent(stored -> {
            // Another instance may have picked it up after the rollback released the lock.
            if (stored.getStatus() != StripeEventStatus.PENDING) {
                return;
            }
            int attempts = stored.getAttempts() + 1;
            stored.setAttempts(attempts);
            stored.setLastError(truncate(String.valueOf(error)));
            if (attempts >= paymentProperties.eventMaxAttempts()) {
                finish(stored, StripeEventStatus.FAILED, now);
                log.error("Stripe event failed for good [stripeEventId={}, type={}, attempts={}]",
                        stored.getStripeEventId(), stored.getEventType(), attempts, error);
                return;
            }
            stored.setNextAttemptAt(now.plus(retryDelay(attempts)));
            log.warn("Stripe event failed, will retry [stripeEventId={}, type={}, attempts={}, nextAttemptAt={}]",
                    stored.getStripeEventId(), stored.getEventType(), attempts, stored.getNextAttemptAt(), error);
        });
    }

    private static void finish(StripeEvent stored, StripeEventStatus status, Instant now) {
        stored.setStatus(status);
        stored.setProcessedAt(now);
        stored.setNextAttemptAt(null);
    }

    private static Duration retryDelay(int attempts) {
        // 30s, 1m, 2m, 4m ... capped at an hour.
        long factor = 1L << Math.min(attempts - 1, 20);
        Duration delay = FIRST_RETRY_DELAY.multipliedBy(factor);
        return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
    }

    private static String truncate(String value) {
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
