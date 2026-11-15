package com.triphuddle.api.payment.controller;

import com.triphuddle.api.payment.service.StripeWebhookService;
import com.triphuddle.stripe.webhook.StripeSignatureException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The endpoint Stripe delivers webhook events to; public, as the signature proves the sender.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments/webhook")
@RequiredArgsConstructor
@Tag(name = "Stripe webhook")
public class StripeWebhookController {

    private final StripeWebhookService stripeWebhookService;

    /**
     * Verify and store a Stripe event; it is handled later, so Stripe gets its answer at once.
     *
     * @param payload the raw request body, which the signature covers byte for byte
     * @param signatureHeader the {@code Stripe-Signature} header
     * @return 200 when stored or a duplicate, 400 when the signature is wrong
     */
    @PostMapping
    @Operation(summary = "Receive a Stripe event", description = "Called by Stripe, not by clients.")
    public ResponseEntity<Void> receive(@RequestBody String payload,
                                        @RequestHeader(value = "Stripe-Signature", required = false)
                                        String signatureHeader) {
        try {
            stripeWebhookService.receive(payload, signatureHeader);
        } catch (StripeSignatureException e) {
            // Stripe retries any non-2xx answer; the refusal also shows in the endpoint's delivery log.
            log.warn("Stripe webhook refused [reason={}]", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }
}
