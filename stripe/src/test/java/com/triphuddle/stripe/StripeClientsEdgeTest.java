package com.triphuddle.stripe;

import com.triphuddle.stripe.catalog.StripeCatalogClient;
import com.triphuddle.stripe.catalog.StripePriceSpec;
import com.triphuddle.stripe.catalog.StripeProductSpec;
import com.triphuddle.stripe.checkout.StripeCheckoutClient;
import com.triphuddle.stripe.checkout.StripeCheckoutSpec;
import com.triphuddle.stripe.customer.StripeCustomerClient;
import com.triphuddle.stripe.exception.StripeIntegrationException;
import com.triphuddle.stripe.invoice.StripeInvoiceClient;
import com.triphuddle.stripe.refund.StripeRefundClient;
import com.triphuddle.stripe.subscription.StripeSubscriptionClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The races, the second-choice paths and the failures of each client.
 */
class StripeClientsEdgeTest {

    private final FakeStripe stripe;

    StripeClientsEdgeTest() throws IOException {
        stripe = new FakeStripe();
    }

    @AfterEach
    void stop() {
        stripe.close();
    }

    private final StripeProductSpec product = new StripeProductSpec("lic-2", "Explorer", "All of it", false, null,
            Map.of());

    @Test
    void aProductAnotherInstanceCreatedMeanwhileIsUpdatedInstead() {
        stripe.on("POST /v1/products/lic-2", 404, "{\"error\": {\"type\": \"invalid_request_error\", "
                        + "\"code\": \"resource_missing\", \"message\": \"x\"}}")
                .on("POST /v1/products/lic-2", "{\"id\": \"lic-2\", \"object\": \"product\"}");
        stripe.failing("POST /v1/products", 400, "resource_already_exists");

        new StripeCatalogClient(stripe.client()).upsertProduct(product);

        assertThat(stripe.requests()).filteredOn(r -> r.path().equals("/v1/products/lic-2")).hasSize(2)
                .allSatisfy(r -> assertThat(r.form()).containsEntry("description", "All of it")
                        .containsEntry("active", "false").doesNotContainKey("tax_code"));
    }

    @Test
    void anExistingProductIsUpdatedWithoutTryingToCreateIt() {
        stripe.on("POST /v1/products/lic-2", "{\"id\": \"lic-2\", \"object\": \"product\"}");

        new StripeCatalogClient(stripe.client()).upsertProduct(product);

        assertThat(stripe.requests()).singleElement().satisfies(r -> {
            assertThat(r.path()).isEqualTo("/v1/products/lic-2");
            assertThat(r.form()).containsEntry("name", "Explorer");
        });
    }

    @Test
    void aFailedCreateOrAFailedUpdateAfterACreateRaceIsWrapped() {
        StripeCatalogClient client = new StripeCatalogClient(stripe.client());
        stripe.failing("POST /v1/products/lic-2", 404, "resource_missing");
        stripe.failing("POST /v1/products", 400, "parameter_invalid");
        assertThatThrownBy(() -> client.upsertProduct(product)).isInstanceOf(StripeIntegrationException.class)
                .hasMessageContaining("Creating Stripe product lic-2");

        stripe.failing("POST /v1/products/lic-5", 404, "resource_missing");
        stripe.failing("POST /v1/products", 400, "resource_already_exists");
        assertThatThrownBy(() -> client.upsertProduct(new StripeProductSpec("lic-5", "X", null, true, null,
                Map.of()))).isInstanceOf(StripeIntegrationException.class)
                .hasMessageContaining("Updating Stripe product lic-5");
    }

    @Test
    void aYearlyPriceIsCreatedToRecurEachYear() {
        stripe.on("GET /v1/prices", "{\"object\": \"list\", \"data\": []}");
        stripe.on("POST /v1/prices", "{\"id\": \"price_y\", \"object\": \"price\", \"active\": true}");

        String price = new StripeCatalogClient(stripe.client())
                .ensurePrice(new StripePriceSpec("lic-3", "lic-3-y", 9999, "EUR", true, Map.of()));

        assertThat(price).isEqualTo("price_y");
        assertThat(stripe.only("POST", "/v1/prices").form()).containsEntry("recurring[interval]", "year")
                .containsEntry("currency", "eur");
    }

    @Test
    void catalogFailuresAreWrapped() {
        StripeCatalogClient client = new StripeCatalogClient(stripe.client());
        stripe.failing("POST /v1/products/lic-2", 400, "parameter_invalid");
        assertThatThrownBy(() -> client.upsertProduct(product)).isInstanceOf(StripeIntegrationException.class);

        stripe.failing("GET /v1/prices", 500, "api_error");
        assertThatThrownBy(() -> client.ensurePrice(new StripePriceSpec("lic-2", "k", 1, "EUR", true, Map.of())))
                .isInstanceOf(StripeIntegrationException.class);
        assertThatThrownBy(() -> client.setDefaultPrice("lic-missing", "price_1"))
                .isInstanceOf(StripeIntegrationException.class);
    }

    @Test
    void aProductCreatedFreshAndAYearlyPrice() {
        StripeCatalogClient client = new StripeCatalogClient(stripe.client());
        stripe.failing("POST /v1/products/lic-3", 404, "resource_missing");
        stripe.on("POST /v1/products", "{\"id\": \"lic-3\", \"object\": \"product\"}");
        stripe.on("GET /v1/prices", "{\"object\": \"list\", \"data\": [{\"id\": \"price_y\", \"object\": \"price\","
                + " \"active\": true}]}");
        StripeProductSpec described = new StripeProductSpec("lic-3", "Year", "Twelve months", true, null, Map.of());

        client.upsertProduct(described);
        String price = client.ensurePrice(new StripePriceSpec("lic-3", "lic-3-y", 9999, "EUR", true, Map.of()));

        assertThat(stripe.only("POST", "/v1/products").form()).containsEntry("description", "Twelve months")
                .doesNotContainKey("tax_code");
        assertThat(price).isEqualTo("price_y");
        assertThat(stripe.requests()).noneMatch(r -> r.path().equals("/v1/prices/price_y"));
    }

    @Test
    void aSwitchWithACouponAndRenewalAndCancelFailures() {
        String subscription = """
                {"id": "sub_9", "object": "subscription", "status": "active",
                 "items": {"object": "list", "data": [{"id": "si_9", "object": "subscription_item",
                   "current_period_start": 1, "current_period_end": 2}]}}""";
        stripe.on("GET /v1/subscriptions/sub_9", subscription).on("POST /v1/subscriptions/sub_9", subscription);
        StripeSubscriptionClient client = new StripeSubscriptionClient(stripe.client());

        assertThat(client.switchPrice("sub_9", "price_3", "coupon_3", Map.of()).metadata()).isEmpty();

        assertThat(stripe.only("POST", "/v1/subscriptions/sub_9").form())
                .containsEntry("discounts[0][coupon]", "coupon_3");
        assertThatThrownBy(() -> client.setCancelAtPeriodEnd("sub_x", false))
                .isInstanceOf(StripeIntegrationException.class);
        assertThatThrownBy(() -> client.cancelNow("sub_x")).isInstanceOf(StripeIntegrationException.class);
        assertThatThrownBy(() -> client.switchPrice("sub_x", "p", null, Map.of()))
                .isInstanceOf(StripeIntegrationException.class);
    }

    @Test
    void aCheckoutWithoutACouponTurnsStripesOwnCodesOff() {
        stripe.on("POST /v1/checkout/sessions", "{\"id\": \"cs_2\", \"object\": \"checkout.session\", "
                + "\"url\": \"https://c\", \"expires_at\": 100}");
        StripeCheckoutClient client = new StripeCheckoutClient(stripe.client());

        client.createSubscriptionSession(new StripeCheckoutSpec("cus_1", "price_1", null, "https://ok",
                "https://cancel", Instant.ofEpochSecond(100), "ls-2", Map.of()));

        assertThat(stripe.only("POST", "/v1/checkout/sessions").form())
                .containsEntry("allow_promotion_codes", "false").doesNotContainKey("discounts[0][coupon]");
        assertThatThrownBy(() -> client.retrieve("cs_missing")).isInstanceOf(StripeIntegrationException.class);
        assertThatThrownBy(() -> client.expireIfOpen("cs_missing")).isInstanceOf(StripeIntegrationException.class);
        stripe.failing("POST /v1/checkout/sessions", 400, "parameter_invalid");
        assertThatThrownBy(() -> client.createSubscriptionSession(new StripeCheckoutSpec("c", "p", null, "u", "u",
                Instant.now(), "r", Map.of()))).isInstanceOf(StripeIntegrationException.class);
    }

    @Test
    void aStaffRefundCarriesNoReasonAndMissingFieldsReadAsEmpty() {
        stripe.on("POST /v1/refunds", "{\"id\": \"re_2\", \"object\": \"refund\", \"status\": \"pending\"}");
        StripeRefundClient client = new StripeRefundClient(stripe.client());

        var refund = client.create("pi_2", 100, false, Map.of(), "k-2");

        assertThat(stripe.only("POST", "/v1/refunds").form()).doesNotContainKey("reason");
        assertThat(refund.amountMinor()).isZero();
        assertThat(refund.metadata()).isEmpty();
        assertThat(refund.createdAt()).isNotNull();
        stripe.failing("POST /v1/refunds", 400, "charge_already_refunded");
        assertThatThrownBy(() -> client.create("pi_2", 100, false, Map.of(), "k-3"))
                .isInstanceOf(StripeIntegrationException.class);
    }

    @Test
    void anInvoicePaidByNoPaymentIntentAndFailures() {
        stripe.on("GET /v1/invoice_payments", "{\"object\": \"list\", \"data\": [{\"id\": \"inpay_2\", "
                + "\"object\": \"invoice_payment\", \"payment\": {\"type\": \"charge\", \"charge\": \"ch_1\"}}]}");
        StripeInvoiceClient client = new StripeInvoiceClient(stripe.client());

        assertThat(client.paidPaymentIntentId("in_2")).isEmpty();
        stripe.failing("GET /v1/invoice_payments", 500, "api_error");
        assertThatThrownBy(() -> client.paidPaymentIntentId("in_3")).isInstanceOf(StripeIntegrationException.class);
        stripe.failing("POST /v1/customers/cus_9", 404, "resource_missing");
        assertThatThrownBy(() -> new StripeCustomerClient(stripe.client()).updateContact("cus_9", "a@b.c", "A"))
                .isInstanceOf(StripeIntegrationException.class);
    }
}
