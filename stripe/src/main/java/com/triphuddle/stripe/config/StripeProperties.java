package com.triphuddle.stripe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code integration.stripe}; zero or negative tolerance, timeouts and retries fall back to defaults.
 *
 * @param secretKey the secret or restricted API key, {@code sk_...} or {@code rk_...}
 * @param webhookSecret the signing secret of the webhook endpoint, {@code whsec_...}
 * @param apiVersion the Stripe API version the service is written against; must match stripe-java's
 * @param webhookToleranceSeconds how old a webhook signature may be before it is refused
 * @param connectTimeoutMs the connect timeout of API calls
 * @param readTimeoutMs the read timeout of API calls
 * @param maxNetworkRetries how often stripe-java retries a failed API call, with idempotency keys
 */
@ConfigurationProperties(prefix = "integration.stripe")
public record StripeProperties(
        String secretKey,
        String webhookSecret,
        String apiVersion,
        long webhookToleranceSeconds,
        int connectTimeoutMs,
        int readTimeoutMs,
        int maxNetworkRetries
) {

    public StripeProperties {
        webhookToleranceSeconds = webhookToleranceSeconds <= 0 ? 300 : webhookToleranceSeconds;
        connectTimeoutMs = connectTimeoutMs <= 0 ? 10_000 : connectTimeoutMs;
        readTimeoutMs = readTimeoutMs <= 0 ? 30_000 : readTimeoutMs;
        maxNetworkRetries = maxNetworkRetries <= 0 ? 2 : maxNetworkRetries;
    }

    /**
     * Check if the key is a live-mode key, which charges real money.
     *
     * @return true for {@code sk_live_} and {@code rk_live_} keys
     */
    public boolean isLiveMode() {
        return secretKey != null && (secretKey.startsWith("sk_live_") || secretKey.startsWith("rk_live_"));
    }
}
