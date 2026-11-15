package com.triphuddle.api.payment.service;

import com.stripe.model.Event;
import com.triphuddle.api.payment.repository.StripeEventRepository;
import com.triphuddle.stripe.webhook.StripeWebhooks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Receives Stripe webhooks: verifies them and stores each event once, leaving the work to the event job.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    private final StripeWebhooks stripeWebhooks;
    private final StripeEventRepository stripeEventRepository;

    /**
     * Verify a webhook request and store its event, unless it was stored already.
     *
     * @param payload the raw request body
     * @param signatureHeader the {@code Stripe-Signature} header
     * @throws com.triphuddle.stripe.webhook.StripeSignatureException if the request is not from Stripe
     */
    @Transactional
    public void receive(String payload, String signatureHeader) {
        Event event = stripeWebhooks.verify(payload, signatureHeader);
        int stored = stripeEventRepository.insertIfAbsent(event.getId(), event.getType(), event.getApiVersion(),
                Boolean.TRUE.equals(event.getLivemode()), Instant.ofEpochSecond(event.getCreated()), payload);
        if (stored == 0) {
            log.info("Duplicate Stripe event skipped [stripeEventId={}, type={}]", event.getId(), event.getType());
            return;
        }
        log.info("Stripe event received [stripeEventId={}, type={}]", event.getId(), event.getType());
    }
}
