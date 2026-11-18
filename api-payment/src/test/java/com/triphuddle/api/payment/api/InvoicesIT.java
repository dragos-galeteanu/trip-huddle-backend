package com.triphuddle.api.payment.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.stripe.refund.StripeRefund;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PAY-5: every invoice and refund is recorded; refunds never exceed what is left, and a double click makes one.
 */
class InvoicesIT extends PaymentApiSupport {

    private UUID user;

    @org.junit.jupiter.api.BeforeEach
    void aVerifiedPayer() {
        user = payer(true);
    }

    @Test
    void staffRefundPartOfAnInvoiceThenTheRestButNeverMore() throws Exception {
        UUID invoice = paidInvoice(999);
        givenRefundsSucceed();

        refund(invoice, Map.of("amountMinor", 400)).andExpect(status().isCreated()).andExpect(jsonPath("$.amountMinor")
                .value(400));
        refund(invoice, Map.of("amountMinor", 600)).andExpect(status().isUnprocessableEntity());
        refund(invoice, Map.of()).andExpect(status().isCreated()).andExpect(jsonPath("$.amountMinor").value(599));
        refund(invoice, Map.of()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("nothing to refund")));

        mvc.perform(get(INVOICES + "/" + invoice).with(Caller.CUSTOMER.request(user)))
                .andExpect(jsonPath("$.amountRefundedMinor").value(999))
                .andExpect(jsonPath("$.refunds.length()").value(2));
    }

    @Test
    void theSameRefundTwiceUsesOneIdempotencyKey() throws Exception {
        UUID invoice = paidInvoice(999);
        given(stripeRefunds.create(anyString(), anyLong(), anyBoolean(), any(), anyString())).willReturn(
                new StripeRefund("re_same", "pi_x", 300, "eur", "succeeded", null, Instant.now(), Map.of()));

        refund(invoice, Map.of("amountMinor", 300)).andExpect(status().isCreated());
        // The second request has left nothing changed, so it carries the same key and Stripe answers with the same
        // refund, which is booked once.
        jdbc.update("update payment_invoices set amount_refunded_minor = 0 where id = ?", invoice);
        refund(invoice, Map.of("amountMinor", 300)).andExpect(status().isCreated());

        then(stripeRefunds).should(org.mockito.Mockito.times(2)).create(anyString(), eq(300L), anyBoolean(), any(),
                eq("refund-%s-0-300".formatted(invoice)));
        assertThat(jdbc.queryForObject("select count(*) from payment_refunds r join payment_invoices i "
                + "on i.id = r.invoice_id where i.id = ?", Integer.class, invoice)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select amount_refunded_minor from payment_invoices where id = ?", Long.class,
                invoice)).isEqualTo(300);
    }

    @Test
    void aRefundMadeInTheDashboardIsBookedFromItsEvent() throws Exception {
        UUID invoice = paidInvoice(999);
        String paymentIntent = jdbc.queryForObject("select stripe_payment_intent_id from payment_invoices where id = ?",
                String.class, invoice);

        webhook(event("refund.created", """
                {"id": "re_dash_%s", "object": "refund", "payment_intent": "%s", "amount": 250, "currency": "eur",
                 "status": "succeeded", "created": %d, "metadata": {}}"""
                .formatted(invoice, paymentIntent, Instant.now().getEpochSecond()))).andExpect(status().isOk());
        stripeEventJob.processDue();

        assertThat(jdbc.queryForObject("select amount_refunded_minor from payment_invoices where id = ?", Long.class,
                invoice)).isEqualTo(250);
    }

    @Test
    void customersSeeTheirOwnInvoicesStaffSeeAllAndOnlyStaffRefund() throws Exception {
        UUID invoice = paidInvoice(999);

        JsonNode mine = body(mvc.perform(get(INVOICES + "/mine").param("limit", "10").with(Caller.CUSTOMER.request(user)))
                .andExpect(status().isOk()));
        assertThat(mine.path("data").findValuesAsText("id")).contains(invoice.toString());
        mvc.perform(get(INVOICES + "/" + invoice).with(Caller.CUSTOMER.request())).andExpect(status().isForbidden());
        mvc.perform(get(INVOICES).param("page", "1").param("pageSize", "5").with(Caller.INSPECTOR.request()))
                .andExpect(status().isOk());
        mvc.perform(get(INVOICES).param("page", "1").param("pageSize", "5").param("subscriberId", user.toString())
                .with(Caller.INSPECTOR.request())).andExpect(jsonPath("$.data.length()").value(1));
        refund(invoice, Map.of("amountMinor", 1), Caller.INSPECTOR).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payments/subscriptions").param("page", "1").param("pageSize", "5")
                .with(Caller.INSPECTOR.request())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/payments/subscriptions").param("page", "1").param("pageSize", "5")
                .param("isRefundDue", "false").param("subscriberId", user.toString())
                .with(Caller.INSPECTOR.request())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void refundingAndEndingStopsTheStripeSubscription() throws Exception {
        UUID invoice = paidInvoice(999);
        givenRefundsSucceed();
        String stripeSubscription = jdbc.queryForObject("select stripe_subscription_id from payment_invoices where id = ?",
                String.class, invoice);
        UUID subscription = jdbc.queryForObject("select license_subscription_id from payment_invoices where id = ?",
                UUID.class, invoice);
        licenseEvents.onSubscriptionActivated(new com.triphuddle.amqp.license.LicenseSubscriptionActivatedEvent(
                subscription, UUID.randomUUID(), "PLAN", "Explorer", com.triphuddle.amqp.license.LicenseTarget.USER,
                user, user, periodEnd, true, false, 999, "EUR", Map.of()));

        refund(invoice, Map.of("isEndingSubscription", true)).andExpect(status().isCreated());

        then(stripeSubscriptions).should().cancelNow(stripeSubscription);
    }

    private UUID paidInvoice(long amount) throws Exception {
        UUID subscription = pending(syncedLicense(amount), user, amount, amount, null);
        String stripeSubscription = paidThroughCheckout(subscription);
        String invoiceId = "in_" + UUID.randomUUID();
        given(stripeInvoices.paidPaymentIntentId(invoiceId)).willReturn(Optional.of("pi_" + UUID.randomUUID()));
        webhook(event("invoice.paid", invoice(invoiceId, stripeSubscription, "subscription_create", "paid", amount)))
                .andExpect(status().isOk());
        stripeEventJob.processDue();
        return jdbc.queryForObject("select id from payment_invoices where stripe_invoice_id = ?", UUID.class, invoiceId);
    }

    private void givenRefundsSucceed() {
        given(stripeRefunds.create(anyString(), anyLong(), anyBoolean(), any(), anyString())).willAnswer(i ->
                new StripeRefund("re_" + UUID.randomUUID(), i.getArgument(0), i.getArgument(1), "eur", "succeeded",
                        null, Instant.now(), Map.of()));
    }

    private org.springframework.test.web.servlet.ResultActions refund(UUID invoice, Map<String, Object> body)
            throws Exception {
        return refund(invoice, body, Caller.SUPPORT);
    }

    private org.springframework.test.web.servlet.ResultActions refund(UUID invoice, Map<String, Object> body,
                                                                      Caller caller) throws Exception {
        return send(post(INVOICES + "/" + invoice + "/refund"), body, caller.request());
    }
}
