package com.triphuddle.stripe.config;

import com.stripe.Stripe;
import com.stripe.StripeClient;
import com.triphuddle.stripe.catalog.StripeCatalogClient;
import com.triphuddle.stripe.checkout.StripeCheckoutClient;
import com.triphuddle.stripe.customer.StripeCustomerClient;
import com.triphuddle.stripe.invoice.StripeInvoiceClient;
import com.triphuddle.stripe.refund.StripeRefundClient;
import com.triphuddle.stripe.subscription.StripeSubscriptionClient;
import com.triphuddle.stripe.webhook.StripeWebhooks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link StripeClient}, {@link StripeWebhooks} and the API clients, and refuses to start on missing or
 * mismatched settings.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(StripeProperties.class)
public class StripeConfig {

    /**
     * The Stripe API client, after the settings are checked.
     *
     * @param properties the Stripe settings
     * @return the client
     */
    @Bean
    public StripeClient stripeClient(StripeProperties properties) {
        validate(properties);
        log.info("Stripe client configured [apiVersion={}, isLiveMode={}]", Stripe.API_VERSION,
                properties.isLiveMode());
        return StripeClient.builder()
                .setApiKey(properties.secretKey())
                .setConnectTimeout(properties.connectTimeoutMs())
                .setReadTimeout(properties.readTimeoutMs())
                .setMaxNetworkRetries(properties.maxNetworkRetries())
                .build();
    }

    /**
     * The webhook signature verifier and event parser.
     *
     * @param stripeClient the Stripe API client
     * @param properties the Stripe settings
     * @return the webhook helper
     */
    @Bean
    public StripeWebhooks stripeWebhooks(StripeClient stripeClient, StripeProperties properties) {
        return new StripeWebhooks(stripeClient, properties);
    }

    /**
     * The client for Stripe Products and Prices.
     *
     * @param stripeClient the Stripe API client
     * @return the catalog client
     */
    @Bean
    public StripeCatalogClient stripeCatalogClient(StripeClient stripeClient) {
        return new StripeCatalogClient(stripeClient);
    }

    /**
     * The client for Stripe Customers.
     *
     * @param stripeClient the Stripe API client
     * @return the customer client
     */
    @Bean
    public StripeCustomerClient stripeCustomerClient(StripeClient stripeClient) {
        return new StripeCustomerClient(stripeClient);
    }

    /**
     * The client for Checkout Sessions and their coupons.
     *
     * @param stripeClient the Stripe API client
     * @return the checkout client
     */
    @Bean
    public StripeCheckoutClient stripeCheckoutClient(StripeClient stripeClient) {
        return new StripeCheckoutClient(stripeClient);
    }

    /**
     * The client for Stripe Subscriptions.
     *
     * @param stripeClient the Stripe API client
     * @return the subscription client
     */
    @Bean
    public StripeSubscriptionClient stripeSubscriptionClient(StripeClient stripeClient) {
        return new StripeSubscriptionClient(stripeClient);
    }

    /**
     * The client for how invoices were paid.
     *
     * @param stripeClient the Stripe API client
     * @return the invoice client
     */
    @Bean
    public StripeInvoiceClient stripeInvoiceClient(StripeClient stripeClient) {
        return new StripeInvoiceClient(stripeClient);
    }

    /**
     * The client for refunds.
     *
     * @param stripeClient the Stripe API client
     * @return the refund client
     */
    @Bean
    public StripeRefundClient stripeRefundClient(StripeClient stripeClient) {
        return new StripeRefundClient(stripeClient);
    }

    private static void validate(StripeProperties properties) {
        if (isBlank(properties.secretKey())
                || !(properties.secretKey().startsWith("sk_") || properties.secretKey().startsWith("rk_"))) {
            throw new IllegalStateException(
                    "integration.stripe.secret-key (STRIPE_SECRET_KEY) must be an sk_ or rk_ key");
        }
        if (isBlank(properties.webhookSecret()) || !properties.webhookSecret().startsWith("whsec_")) {
            throw new IllegalStateException(
                    "integration.stripe.webhook-secret (STRIPE_WEBHOOK_SECRET) must be a whsec_ secret");
        }
        // stripe-java sends its compiled-in version on every call and parses into models generated
        // for it, so a configured version that differs means the code was written for another API.
        if (!Stripe.API_VERSION.equals(properties.apiVersion())) {
            throw new IllegalStateException(
                    "integration.stripe.api-version is %s but stripe-java %s speaks %s; upgrade both together"
                            .formatted(properties.apiVersion(), Stripe.VERSION, Stripe.API_VERSION));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
