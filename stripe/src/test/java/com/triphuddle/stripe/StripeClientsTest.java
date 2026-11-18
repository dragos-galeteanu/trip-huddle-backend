package com.triphuddle.stripe;

import com.triphuddle.stripe.catalog.StripeCatalogClient;
import com.triphuddle.stripe.catalog.StripePriceSpec;
import com.triphuddle.stripe.catalog.StripeProductSpec;
import com.triphuddle.stripe.checkout.StripeCheckoutClient;
import com.triphuddle.stripe.checkout.StripeCheckoutSession;
import com.triphuddle.stripe.checkout.StripeCheckoutSpec;
import com.triphuddle.stripe.customer.StripeCustomerClient;
import com.triphuddle.stripe.exception.StripeIntegrationException;
import com.triphuddle.stripe.invoice.StripeInvoiceClient;
import com.triphuddle.stripe.refund.StripeRefund;
import com.triphuddle.stripe.refund.StripeRefundClient;
import com.triphuddle.stripe.subscription.StripeSubscriptionClient;
import com.triphuddle.stripe.subscription.StripeSubscriptionPeriod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What each client sends Stripe and how it reads the answer, against a fake Stripe on localhost.
 */
class StripeClientsTest {

    private final FakeStripe stripe;

    StripeClientsTest() throws IOException {
        stripe = new FakeStripe();
    }

    @AfterEach
    void stop() {
        stripe.close();
    }

    private static final String SUBSCRIPTION = """
            {"id": "sub_1", "object": "subscription", "status": "active", "cancel_at_period_end": %s,
             "metadata": {"licenseSubscriptionId": "ls-1"},
             "items": {"object": "list", "data": [{"id": "si_1", "object": "subscription_item",
               "current_period_start": 1767225600, "current_period_end": 1769904000}]}}""";

    @Nested
    class Customers {

        @Test
        void createsACustomerWithTheIdempotencyKeySoARetryMakesNoSecond() {
            stripe.on("POST /v1/customers", "{\"id\": \"cus_1\", \"object\": \"customer\"}");

            String id = new StripeCustomerClient(stripe.client()).create("ana@example.com", "Ana Pop",
                    Map.of("subscriberId", "u-1"), "customer-USER-u-1");

            FakeStripe.Request sent = stripe.only("POST", "/v1/customers");
            assertThat(id).isEqualTo("cus_1");
            assertThat(sent.idempotencyKey()).isEqualTo("customer-USER-u-1");
            assertThat(sent.form()).containsEntry("email", "ana@example.com").containsEntry("name", "Ana Pop")
                    .containsEntry("metadata[subscriberId]", "u-1");
        }

        @Test
        void updatesTheContactAndLeavesOutAMissingName() {
            stripe.on("POST /v1/customers/cus_1", "{\"id\": \"cus_1\", \"object\": \"customer\"}");

            new StripeCustomerClient(stripe.client()).updateContact("cus_1", "new@example.com", null);

            assertThat(stripe.only("POST", "/v1/customers/cus_1").form()).containsEntry("email", "new@example.com")
                    .doesNotContainKey("name");
        }

        @Test
        void wrapsStripesErrors() {
            stripe.failing("POST /v1/customers", 400, "email_invalid");

            assertThatThrownBy(() -> new StripeCustomerClient(stripe.client()).create("x", null, Map.of(), "k"))
                    .isInstanceOf(StripeIntegrationException.class).hasMessageContaining("Creating Stripe customer");
        }
    }

    @Nested
    class Checkout {

        private final StripeCheckoutClient client = new StripeCheckoutClient(stripe.client());

        @Test
        void opensATaxedSubscriptionCheckoutWithOurCouponOrNoCodesAtAll() {
            String session = """
                    {"id": "cs_1", "object": "checkout.session", "url": "https://checkout.stripe.com/cs_1",
                     "expires_at": 1767225600}""";
            stripe.on("POST /v1/checkout/sessions", session);
            Instant expires = Instant.ofEpochSecond(1767225600);

            StripeCheckoutSession opened = client.createSubscriptionSession(new StripeCheckoutSpec("cus_1",
                    "price_1", "coupon_1", "https://app/ok", "https://app/cancel", expires, "ls-1",
                    Map.of("licenseSubscriptionId", "ls-1")));

            assertThat(opened.url()).isEqualTo("https://checkout.stripe.com/cs_1");
            assertThat(opened.expiresAt()).isEqualTo(expires);
            assertThat(stripe.only("POST", "/v1/checkout/sessions").form())
                    .containsEntry("mode", "subscription")
                    .containsEntry("customer", "cus_1")
                    .containsEntry("line_items[0][price]", "price_1")
                    .containsEntry("automatic_tax[enabled]", "true")
                    .containsEntry("discounts[0][coupon]", "coupon_1")
                    .containsEntry("client_reference_id", "ls-1")
                    .containsEntry("subscription_data[metadata][licenseSubscriptionId]", "ls-1")
                    .doesNotContainKey("allow_promotion_codes");
        }

        @Test
        void aCouponAlreadyMadeIsFineAnyOtherFailureIsNot() {
            stripe.failing("POST /v1/coupons", 400, "resource_already_exists");

            client.ensureCoupon("promo-1", 200, "EUR", 3L, "Spring", Map.of());

            assertThat(stripe.only("POST", "/v1/coupons").form()).containsEntry("currency", "eur")
                    .containsEntry("duration", "repeating").containsEntry("duration_in_months", "3")
                    .containsEntry("max_redemptions", "1");
        }

        @Test
        void anyOtherCouponFailureIsAnError() {
            stripe.failing("POST /v1/coupons", 400, "parameter_invalid_integer");

            assertThatThrownBy(() -> client.ensureCoupon("promo-3", -1, "EUR", null, "Bad", Map.of()))
                    .isInstanceOf(StripeIntegrationException.class).hasMessageContaining("promo-3");
        }

        @Test
        void aCouponWithoutAnEndAppliesForever() {
            stripe.on("POST /v1/coupons", "{\"id\": \"promo-2\", \"object\": \"coupon\"}");

            client.ensureCoupon("promo-2", 200, "EUR", null, "Forever", Map.of("promotionId", "p-2"));

            assertThat(stripe.only("POST", "/v1/coupons").form()).containsEntry("duration", "forever")
                    .containsEntry("metadata[promotionId]", "p-2");
        }

        @Test
        void expiresAnOpenSessionButNotACompleteOne() {
            stripe.on("GET /v1/checkout/sessions/cs_open", "{\"id\": \"cs_open\", \"object\": \"checkout.session\", "
                    + "\"status\": \"open\"}");
            stripe.on("POST /v1/checkout/sessions/cs_open/expire", "{\"id\": \"cs_open\", \"object\": "
                    + "\"checkout.session\", \"status\": \"expired\"}");
            stripe.on("GET /v1/checkout/sessions/cs_done", "{\"id\": \"cs_done\", \"object\": \"checkout.session\", "
                    + "\"status\": \"complete\", \"payment_status\": \"paid\", \"customer\": \"cus_1\", "
                    + "\"subscription\": \"sub_1\"}");

            assertThat(client.expireIfOpen("cs_open")).isTrue();
            assertThat(client.expireIfOpen("cs_done")).isFalse();
            assertThat(client.retrieve("cs_done").subscriptionId()).isEqualTo("sub_1");
        }
    }

    @Nested
    class Subscriptions {

        private final StripeSubscriptionClient client = new StripeSubscriptionClient(stripe.client());

        @Test
        void readsThePeriodFromTheSubscriptionsItem() {
            stripe.on("GET /v1/subscriptions/sub_1", SUBSCRIPTION.formatted("true"));

            StripeSubscriptionPeriod period = client.retrieve("sub_1");

            assertThat(period.periodStart()).isEqualTo(Instant.ofEpochSecond(1767225600));
            assertThat(period.periodEnd()).isEqualTo(Instant.ofEpochSecond(1769904000));
            assertThat(period.isCancelAtPeriodEnd()).isTrue();
            assertThat(period.metadata()).containsEntry("licenseSubscriptionId", "ls-1");
        }

        @Test
        void cancelsNowWithoutProratingAndNotTwice() {
            stripe.on("GET /v1/subscriptions/sub_1", SUBSCRIPTION.formatted("false"));
            stripe.on("DELETE /v1/subscriptions/sub_1", SUBSCRIPTION.formatted("false"));
            stripe.on("GET /v1/subscriptions/sub_gone", "{\"id\": \"sub_gone\", \"object\": \"subscription\", "
                    + "\"status\": \"canceled\"}");

            assertThat(client.cancelNow("sub_1")).isTrue();
            assertThat(stripe.only("DELETE", "/v1/subscriptions/sub_1").form()).containsEntry("prorate", "false")
                    .containsEntry("invoice_now", "false");
            assertThat(client.cancelNow("sub_gone")).isFalse();
        }

        @Test
        void switchesThePriceAtOnceChargingTheDifferenceOrNothingChanges() {
            stripe.on("GET /v1/subscriptions/sub_1", SUBSCRIPTION.formatted("false"));
            stripe.on("POST /v1/subscriptions/sub_1", SUBSCRIPTION.formatted("false"));

            client.switchPrice("sub_1", "price_2", null, Map.of("licenseSubscriptionId", "ls-2"));

            assertThat(stripe.only("POST", "/v1/subscriptions/sub_1").form())
                    .containsEntry("items[0][id]", "si_1").containsEntry("items[0][price]", "price_2")
                    .containsEntry("proration_behavior", "always_invoice")
                    .containsEntry("payment_behavior", "error_if_incomplete")
                    .containsEntry("billing_cycle_anchor[type]", "now")
                    .containsEntry("discounts", "")
                    .containsEntry("metadata[licenseSubscriptionId]", "ls-2");
        }

        @Test
        void changesTheRenewalAndWrapsFailures() {
            stripe.on("POST /v1/subscriptions/sub_1", SUBSCRIPTION.formatted("true"));

            assertThat(client.setCancelAtPeriodEnd("sub_1", true).isCancelAtPeriodEnd()).isTrue();
            assertThat(stripe.only("POST", "/v1/subscriptions/sub_1").form())
                    .containsEntry("cancel_at_period_end", "true");
            assertThatThrownBy(() -> client.retrieve("sub_missing")).isInstanceOf(StripeIntegrationException.class);
        }
    }

    @Nested
    class Catalog {

        private final StripeCatalogClient client = new StripeCatalogClient(stripe.client());
        private final StripeProductSpec product = new StripeProductSpec("lic-1", "Explorer", null, true, "txcd_1",
                Map.of("licenseId", "lic-1"));

        @Test
        void updatesAProductOrCreatesItWhenStripeHasNone() {
            stripe.failing("POST /v1/products/lic-1", 404, "resource_missing");
            stripe.on("POST /v1/products", "{\"id\": \"lic-1\", \"object\": \"product\"}");

            client.upsertProduct(product);

            assertThat(stripe.only("POST", "/v1/products").form()).containsEntry("id", "lic-1")
                    .containsEntry("tax_code", "txcd_1").containsEntry("active", "true");
            assertThat(stripe.only("POST", "/v1/products/lic-1").form()).containsEntry("description", "");
        }

        @Test
        void reusesAPriceByItsLookupKeyReactivatingItIfNeeded() {
            stripe.on("GET /v1/prices", "{\"object\": \"list\", \"data\": [{\"id\": \"price_1\", \"object\": \"price\","
                    + " \"active\": false}]}");
            stripe.on("POST /v1/prices/price_1", "{\"id\": \"price_1\", \"object\": \"price\", \"active\": true}");

            assertThat(client.ensurePrice(price())).isEqualTo("price_1");
            assertThat(stripe.only("POST", "/v1/prices/price_1").form()).containsEntry("active", "true");
        }

        @Test
        void createsATaxInclusivePriceOnceForALookupKey() {
            stripe.on("GET /v1/prices", "{\"object\": \"list\", \"data\": []}");
            stripe.on("POST /v1/prices", "{\"id\": \"price_9\", \"object\": \"price\", \"active\": true}");
            stripe.on("POST /v1/products/lic-1", "{\"id\": \"lic-1\", \"object\": \"product\"}");

            assertThat(client.ensurePrice(price())).isEqualTo("price_9");
            client.setDefaultPrice("lic-1", "price_9");

            FakeStripe.Request created = stripe.only("POST", "/v1/prices");
            assertThat(created.idempotencyKey()).isEqualTo("price-lic-1-999-EUR-MONTH");
            assertThat(created.form()).containsEntry("tax_behavior", "inclusive")
                    .containsEntry("recurring[interval]", "month").containsEntry("currency", "eur")
                    .containsEntry("unit_amount", "999");
            assertThat(stripe.only("POST", "/v1/products/lic-1").form()).containsEntry("default_price", "price_9");
        }

        private StripePriceSpec price() {
            return new StripePriceSpec("lic-1", "lic-1-999-EUR-MONTH", 999, "EUR", false, Map.of());
        }
    }

    @Nested
    class RefundsAndInvoices {

        @Test
        void refundsWithTheIdempotencyKeySoADoubleClickMakesOneRefund() {
            stripe.on("POST /v1/refunds", """
                    {"id": "re_1", "object": "refund", "payment_intent": "pi_1", "amount": 500, "currency": "eur",
                     "status": "succeeded", "created": 1767225600, "metadata": {"invoiceId": "inv-1"}}""");

            StripeRefund refund = new StripeRefundClient(stripe.client()).create("pi_1", 500, true,
                    Map.of("invoiceId", "inv-1"), "refund-inv-1-1");

            FakeStripe.Request sent = stripe.only("POST", "/v1/refunds");
            assertThat(sent.idempotencyKey()).isEqualTo("refund-inv-1-1");
            assertThat(sent.form()).containsEntry("reason", "requested_by_customer").containsEntry("amount", "500");
            assertThat(refund.amountMinor()).isEqualTo(500);
            assertThat(refund.metadata()).containsEntry("invoiceId", "inv-1");
        }

        @Test
        void findsThePaymentThatPaidAnInvoice() {
            stripe.on("GET /v1/invoice_payments", """
                    {"object": "list", "data": [
                      {"id": "inpay_1", "object": "invoice_payment", "payment": {"type": "payment_intent",
                       "payment_intent": "pi_7"}}]}""");

            assertThat(new StripeInvoiceClient(stripe.client()).paidPaymentIntentId("in_1")).contains("pi_7");
            assertThat(stripe.only("GET", "/v1/invoice_payments").form()).containsEntry("invoice", "in_1")
                    .containsEntry("status", "paid");
        }
    }
}
