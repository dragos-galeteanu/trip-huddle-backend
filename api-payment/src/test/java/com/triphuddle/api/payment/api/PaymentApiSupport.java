package com.triphuddle.api.payment.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.triphuddle.amqp.license.BillingInterval;
import com.triphuddle.amqp.license.LicenseCatalogChangedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionPendingEvent;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.amqp.user.UserContactUpdatedEvent;
import com.triphuddle.api.payment.job.StripeEventJob;
import com.triphuddle.api.payment.messaging.LicenseEventListener;
import com.triphuddle.api.payment.messaging.UserEventListener;
import com.triphuddle.api.payment.service.CatalogSyncService;
import com.triphuddle.stripe.catalog.StripeCatalogClient;
import com.triphuddle.stripe.checkout.StripeCheckoutClient;
import com.triphuddle.stripe.customer.StripeCustomerClient;
import com.triphuddle.stripe.invoice.StripeInvoiceClient;
import com.triphuddle.stripe.refund.StripeRefundClient;
import com.triphuddle.stripe.subscription.StripeSubscriptionClient;
import com.triphuddle.stripe.subscription.StripeSubscriptionPeriod;
import com.triphuddle.testsupport.IntegrationTest;
import com.triphuddle.testsupport.messaging.RecordingMessageProducer;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * api-payment's integration tests share this: Stripe's clients are mocks, webhooks are signed with the test secret,
 * and shortcuts seed what api-license and api-user would have sent.
 */
@IntegrationTest
@Import(PaymentApiSupport.StripeMocks.class)
abstract class PaymentApiSupport {

    static final String WEBHOOK_SECRET = "whsec_integration_test";
    static final String CHECKOUT = "/api/v1/payments/checkout";
    static final String INVOICES = "/api/v1/payments/invoices";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @TestConfiguration(proxyBeanMethods = false)
    static class StripeMocks {
        @Bean @Primary StripeCatalogClient catalogMock() { return Mockito.mock(StripeCatalogClient.class); }
        @Bean @Primary StripeCheckoutClient checkoutMock() { return Mockito.mock(StripeCheckoutClient.class); }
        @Bean @Primary StripeCustomerClient customerMock() { return Mockito.mock(StripeCustomerClient.class); }
        @Bean @Primary StripeInvoiceClient invoiceMock() { return Mockito.mock(StripeInvoiceClient.class); }
        @Bean @Primary StripeRefundClient refundMock() { return Mockito.mock(StripeRefundClient.class); }
        @Bean @Primary StripeSubscriptionClient subscriptionMock() {
            return Mockito.mock(StripeSubscriptionClient.class);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired RecordingMessageProducer published;
    @Autowired LicenseEventListener licenseEvents;
    @Autowired UserEventListener userEvents;
    @Autowired CatalogSyncService catalogSync;
    @Autowired StripeEventJob stripeEventJob;
    @Autowired StripeCatalogClient stripeCatalog;
    @Autowired StripeCheckoutClient stripeCheckout;
    @Autowired StripeCustomerClient stripeCustomers;
    @Autowired StripeInvoiceClient stripeInvoices;
    @Autowired StripeRefundClient stripeRefunds;
    @Autowired StripeSubscriptionClient stripeSubscriptions;

    final Instant periodStart = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    final Instant periodEnd = periodStart.plus(Duration.ofDays(30));

    @BeforeEach
    void resetStripeAndMessages() {
        Mockito.reset(stripeCatalog, stripeCheckout, stripeCustomers, stripeInvoices, stripeRefunds,
                stripeSubscriptions);
        given(stripeCatalog.ensurePrice(any())).willAnswer(i -> "price_" + SEQ.incrementAndGet());
        given(stripeCustomers.create(anyString(), any(), any(), anyString()))
                .willAnswer(i -> "cus_" + SEQ.incrementAndGet());
        published.reset();
    }

    /** A published license, as api-license announced it, already pushed to Stripe. */
    UUID syncedLicense(long priceMinor) {
        UUID licenseId = UUID.randomUUID();
        licenseEvents.onCatalogChanged(new LicenseCatalogChangedEvent(licenseId, "PLAN_" + SEQ.incrementAndGet(),
                "Explorer", null, LicenseTarget.USER, "PUBLISHED", priceMinor, "EUR", BillingInterval.MONTH,
                Instant.now()));
        while (catalogSync.syncNext(Instant.now().plusSeconds(1)).isPresent()) {
            // earlier tests' products may be due as well
        }
        return licenseId;
    }

    /** A user as api-user announced them. */
    UUID payer(boolean isVerified) {
        UUID userId = UUID.randomUUID();
        userEvents.onContactUpdated(new UserContactUpdatedEvent(userId, "payer-" + userId + "@example.com", "Ana",
                "Pop", isVerified, Instant.now()));
        return userId;
    }

    /** A subscription waiting for payment, as api-license announced it. */
    UUID pending(UUID licenseId, UUID userId, long listPriceMinor, long firstPriceMinor, Integer promotionPeriods) {
        UUID subscriptionId = UUID.randomUUID();
        Instant now = Instant.now();
        licenseEvents.onSubscriptionPending(new LicenseSubscriptionPendingEvent(subscriptionId, licenseId,
                LicenseTarget.USER, userId, userId, BillingInterval.MONTH, listPriceMinor, firstPriceMinor, "EUR",
                promotionPeriods, now, now.plus(Duration.ofHours(48))));
        return subscriptionId;
    }

    /** Paid through checkout, the way Stripe tells it: a signed checkout.session.completed, then processed. */
    String paidThroughCheckout(UUID subscriptionId) throws Exception {
        String stripeSubscriptionId = "sub_" + SEQ.incrementAndGet();
        given(stripeSubscriptions.retrieve(stripeSubscriptionId)).willReturn(new StripeSubscriptionPeriod(
                stripeSubscriptionId, "active", periodStart, periodEnd, false, Map.of()));
        webhook(event("checkout.session.completed", """
                {"id": "cs_%d", "object": "checkout.session", "mode": "subscription", "client_reference_id": "%s",
                 "payment_status": "paid", "status": "complete", "customer": "cus_1", "subscription": "%s"}"""
                .formatted(SEQ.incrementAndGet(), subscriptionId, stripeSubscriptionId))).andExpect(status().isOk());
        stripeEventJob.processDue();
        return stripeSubscriptionId;
    }

    static String event(String type, String object) {
        return """
                {"id": "evt_%s", "object": "event", "type": "%s", "api_version": "%s", "created": %d,
                 "livemode": false, "data": {"object": %s}}"""
                .formatted(UUID.randomUUID().toString().replace("-", ""), type, Stripe.API_VERSION,
                        Instant.now().getEpochSecond(), object);
    }

    static String invoice(String id, String stripeSubscriptionId, String billingReason, String status, long amount) {
        long now = Instant.now().getEpochSecond();
        return """
                {"id": "%s", "object": "invoice", "number": "INV-%s", "billing_reason": "%s", "status": "%s",
                 "total": %d, "amount_paid": %d, "amount_due": %d, "currency": "eur",
                 "hosted_invoice_url": "https://invoice.example/%s", "invoice_pdf": "https://invoice.example/%s.pdf",
                 "created": %d, "status_transitions": {"paid_at": %d}, "next_payment_attempt": %d,
                 "total_taxes": [{"amount": 160}],
                 "parent": {"type": "subscription_details", "subscription_details": {"subscription": "%s"}}}"""
                .formatted(id, id, billingReason, status, amount, "paid".equals(status) ? amount : 0, amount, id, id,
                        now, now, now + 86_400, stripeSubscriptionId);
    }

    ResultActions webhook(String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        return mvc.perform(post("/api/v1/payments/webhook").contentType(MediaType.APPLICATION_JSON).content(payload)
                .header("Stripe-Signature", "t=%d,v1=%s".formatted(timestamp, hmac(timestamp + "." + payload))));
    }

    ResultActions send(MockHttpServletRequestBuilder request, Object body, RequestPostProcessor caller)
            throws Exception {
        return mvc.perform(request.with(caller).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)));
    }

    JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    <T> T billing(UUID subscriptionId, String column, Class<T> type) {
        return jdbc.queryForObject("select " + column
                + " from payment_subscriptions where license_subscription_id = ?", type, subscriptionId);
    }

    private static String hmac(String signed) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
    }
}
