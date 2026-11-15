package com.triphuddle.api.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code app.payment}; a blank tax code means the Stripe account's default.
 *
 * @param eventIntervalMs how often the inbox of received Stripe events is worked through
 * @param eventMaxAttempts how often a failing event is tried before it is left {@code FAILED}
 * @param catalogSyncIntervalMs how often changed licenses are pushed to Stripe
 * @param productTaxCode the Stripe Tax code of license products, e.g. {@code txcd_10103001}
 * @param checkoutSuccessUrl where Stripe sends a customer who paid; {@code {subscriptionId}} is replaced
 * @param checkoutCancelUrl where Stripe sends a customer who went back; {@code {subscriptionId}} is replaced
 * @param subscriptionUrl the subscription's page, linked from payment emails; {@code {subscriptionId}} is replaced
 * @param reconcileIntervalMs how often the reconciliation job runs
 * @param reconcileEveryHours how long after its last comparison a subscription is compared with Stripe again
 */
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
        long eventIntervalMs,
        int eventMaxAttempts,
        long catalogSyncIntervalMs,
        String productTaxCode,
        String checkoutSuccessUrl,
        String checkoutCancelUrl,
        String subscriptionUrl,
        long reconcileIntervalMs,
        int reconcileEveryHours
) {

    public PaymentProperties {
        productTaxCode = productTaxCode == null || productTaxCode.isBlank() ? null : productTaxCode.strip();
        reconcileEveryHours = reconcileEveryHours <= 0 ? 24 : reconcileEveryHours;
    }
}
