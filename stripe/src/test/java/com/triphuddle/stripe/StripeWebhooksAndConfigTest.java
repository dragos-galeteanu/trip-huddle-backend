package com.triphuddle.stripe;

import com.stripe.Stripe;
import com.stripe.model.Event;
import com.triphuddle.stripe.config.StripeConfig;
import com.triphuddle.stripe.config.StripeProperties;
import com.triphuddle.stripe.webhook.StripeSignatureException;
import com.triphuddle.stripe.webhook.StripeWebhooks;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StripeWebhooksAndConfigTest {

    static final String SECRET = "whsec_test_secret";

    /** Signs a payload the way Stripe does: HMAC-SHA256 of "timestamp.payload" with the endpoint's secret. */
    static String sign(String payload, long timestamp, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload)
                    .getBytes(StandardCharsets.UTF_8)));
            return "t=" + timestamp + ",v1=" + signature;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String event(String id, String apiVersion) {
        return """
                {"id": "%s", "object": "event", "type": "invoice.paid", "api_version": "%s", "created": %d,
                 "livemode": false, "data": {"object": {"id": "in_1", "object": "invoice"}}}"""
                .formatted(id, apiVersion, Instant.now().getEpochSecond());
    }

    private static StripeProperties properties(String key, String secret, String version) {
        return new StripeProperties(key, secret, version, 300, 0, 0, 0);
    }

    @Nested
    class Webhooks {

        private final StripeWebhooks webhooks = new StripeWebhooks(
                new StripeConfig().stripeClient(properties("sk_test_x", SECRET, Stripe.API_VERSION)),
                properties("sk_test_x", SECRET, Stripe.API_VERSION));

        @Test
        void acceptsAnEventSignedWithTheEndpointsSecret() {
            String payload = event("evt_1", Stripe.API_VERSION);

            Event event = webhooks.verify(payload, sign(payload, Instant.now().getEpochSecond(), SECRET));

            assertThat(event.getId()).isEqualTo("evt_1");
            assertThat(webhooks.isReadable(event)).isTrue();
        }

        @Test
        void refusesAMissingWrongOrStaleSignature() {
            String payload = event("evt_2", Stripe.API_VERSION);
            long now = Instant.now().getEpochSecond();

            assertThatThrownBy(() -> webhooks.verify(payload, null)).isInstanceOf(StripeSignatureException.class);
            assertThatThrownBy(() -> webhooks.verify(payload, " ")).isInstanceOf(StripeSignatureException.class);
            assertThatThrownBy(() -> webhooks.verify(payload, sign(payload, now, "whsec_other")))
                    .isInstanceOf(StripeSignatureException.class);
            assertThatThrownBy(() -> webhooks.verify(payload, sign(payload, now - 301, SECRET)))
                    .as("outside the 300 s tolerance").isInstanceOf(StripeSignatureException.class);
            assertThatThrownBy(() -> webhooks.verify(payload.replace("evt_2", "evt_3"), sign(payload, now, SECRET)))
                    .as("a payload changed after signing").isInstanceOf(StripeSignatureException.class);
        }

        @Test
        void parsesAStoredEventAndTellsWhetherItsVersionIsReadable() {
            Event old = webhooks.parse(event("evt_4", "2020-08-27"));

            assertThat(old.getId()).isEqualTo("evt_4");
            assertThat(webhooks.isReadable(old)).isFalse();
        }
    }

    @Nested
    class Startup {

        @ParameterizedTest(name = "key {0}, secret {1}")
        @CsvSource({"pk_test_x, whsec_x", "'', whsec_x", "sk_test_x, secret", "sk_test_x, ''"})
        void refusesKeysThatAreNotASecretKeyAndAWebhookSecret(String key, String secret) {
            assertThatThrownBy(() -> new StripeConfig().stripeClient(properties(key, secret, Stripe.API_VERSION)))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void refusesAnApiVersionStripeJavaDoesNotSpeak() {
            assertThatThrownBy(() -> new StripeConfig().stripeClient(properties("sk_test_x", "whsec_x", "2020-08-27")))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("upgrade both together");
        }

        @Test
        void acceptsRestrictedKeysTellsLiveFromTestAndFillsDefaults() {
            assertThat(new StripeConfig().stripeClient(properties("rk_test_x", "whsec_x", Stripe.API_VERSION)))
                    .isNotNull();
            assertThat(properties("sk_live_x", "whsec_x", Stripe.API_VERSION).isLiveMode()).isTrue();
            assertThat(properties("rk_live_x", "whsec_x", Stripe.API_VERSION).isLiveMode()).isTrue();
            assertThat(properties("sk_test_x", "whsec_x", Stripe.API_VERSION).isLiveMode()).isFalse();
            StripeProperties defaults = properties("sk_test_x", "whsec_x", Stripe.API_VERSION);
            assertThat(defaults.connectTimeoutMs()).isEqualTo(10_000);
            assertThat(defaults.maxNetworkRetries()).isEqualTo(2);
            assertThat(new StripeProperties("k", "s", "v", 0, 0, 0, 0).webhookToleranceSeconds()).isEqualTo(300);
            StripeProperties set = new StripeProperties("k", "s", "v", 60, 5_000, 20_000, 4);
            assertThat(set.webhookToleranceSeconds()).isEqualTo(60);
            assertThat(set.connectTimeoutMs()).isEqualTo(5_000);
            assertThat(set.readTimeoutMs()).isEqualTo(20_000);
            assertThat(set.maxNetworkRetries()).isEqualTo(4);
            assertThat(properties(null, "whsec_x", Stripe.API_VERSION).isLiveMode()).isFalse();
        }

        @Test
        void wiresEveryClient() {
            StripeConfig config = new StripeConfig();
            var client = config.stripeClient(properties("sk_test_x", "whsec_x", Stripe.API_VERSION));

            assertThat(config.stripeWebhooks(client, properties("sk_test_x", "whsec_x", Stripe.API_VERSION)))
                    .isNotNull();
            assertThat(config.stripeCatalogClient(client)).isNotNull();
            assertThat(config.stripeCustomerClient(client)).isNotNull();
            assertThat(config.stripeCheckoutClient(client)).isNotNull();
            assertThat(config.stripeSubscriptionClient(client)).isNotNull();
            assertThat(config.stripeInvoiceClient(client)).isNotNull();
            assertThat(config.stripeRefundClient(client)).isNotNull();
        }
    }
}
