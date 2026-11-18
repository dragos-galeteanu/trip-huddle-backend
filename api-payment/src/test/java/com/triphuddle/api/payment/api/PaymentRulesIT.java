package com.triphuddle.api.payment.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.amqp.group.GroupMembershipCreatedEvent;
import com.triphuddle.amqp.license.BillingInterval;
import com.triphuddle.amqp.license.LicenseSubscriptionPendingEvent;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.amqp.notification.NotificationEmailType;
import com.triphuddle.amqp.notification.SendEmailRequest;
import com.triphuddle.amqp.user.UserDisabledEvent;
import com.triphuddle.api.payment.messaging.GroupEventListener;
import com.triphuddle.api.payment.service.CatalogSyncService;
import com.triphuddle.api.payment.service.ReconciliationService;
import com.triphuddle.api.payment.service.StripeEventProcessor;
import com.triphuddle.stripe.checkout.StripeCheckoutSession;
import com.triphuddle.stripe.checkout.StripeCheckoutSessionState;
import com.triphuddle.testsupport.messaging.RecordingMessageProducer.Published;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PAY-2, PAY-4, PAY-5 and PAY-6: group invoices and payers, payment emails, expired checkouts, refunds that
 * failed, and how failures are retried.
 */
class PaymentRulesIT extends PaymentApiSupport {

    @Autowired GroupEventListener groupEvents;
    @Autowired ReconciliationService reconciliation;
    @Autowired StripeEventProcessor stripeEvents;

    private final UUID admin = UUID.randomUUID();
    private final UUID group = UUID.randomUUID();

    @Test
    void aGroupsAdminsAndStaffPageThroughItsInvoicesAndNobodyElseSeesThem() throws Exception {
        UUID adminUser = groupAdmin();
        String stripeSubscription = paidThroughCheckout(groupPending(adminUser));
        UUID first = paidInvoice(stripeSubscription, "subscription_create");
        UUID second = paidInvoice(stripeSubscription, "subscription_cycle");

        JsonNode page = body(mvc.perform(get(INVOICES + "/groups/" + group).param("limit", "1")
                .with(Caller.CUSTOMER.request(adminUser))).andExpect(status().isOk()));
        JsonNode next = body(mvc.perform(get(INVOICES + "/groups/" + group).param("limit", "1")
                .param("cursor", page.path("nextCursor").asText()).with(Caller.CUSTOMER.request(adminUser)))
                .andExpect(status().isOk()));

        assertThat(page.path("data")).hasSize(1);
        assertThat(next.path("data")).hasSize(1);
        assertThat(java.util.List.of(page.path("data").get(0).path("id").asText(),
                next.path("data").get(0).path("id").asText()))
                .containsExactlyInAnyOrder(first.toString(), second.toString());
        mvc.perform(get(INVOICES + "/" + first).with(Caller.CUSTOMER.request(adminUser))).andExpect(status().isOk());
        mvc.perform(get(INVOICES + "/groups/" + group).with(Caller.INSPECTOR.request())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(get(INVOICES + "/groups/" + group).with(Caller.CUSTOMER.request())).andExpect(status().isForbidden());
        mvc.perform(get(INVOICES + "/" + first).with(Caller.CUSTOMER.request())).andExpect(status().isForbidden());
    }

    @Test
    void aGroupsPaymentIsEmailedToTheAdminWhoBoughtIt() throws Exception {
        UUID adminUser = groupAdmin();
        String stripeSubscription = paidThroughCheckout(groupPending(adminUser));
        published.reset();

        paidInvoice(stripeSubscription, "subscription_create");

        assertThat(emails()).singleElement().satisfies(email -> {
            assertThat(email.type()).isEqualTo(NotificationEmailType.LICENSE_PAYMENT_SUCCEEDED);
            assertThat(email.recipientEmail()).isEqualTo("payer-" + adminUser + "@example.com");
            assertThat(email.templateData()).containsEntry("isGroup", true).containsEntry("firstName", "Ana");
        });
    }

    @Test
    void anUnverifiedOrDisabledPayerIsNotEmailed() throws Exception {
        UUID unverified = payer(false);
        UUID disabled = payer(true);
        userEvents.onUserDisabled(new UserDisabledEvent(disabled));
        String first = paidThroughCheckout(pending(syncedLicense(999), unverified, 999, 999, null));
        String second = paidThroughCheckout(pending(syncedLicense(999), disabled, 999, 999, null));
        published.reset();

        paidInvoice(first, "subscription_create");
        paidInvoice(second, "subscription_create");

        assertThat(emails()).isEmpty();
    }

    @Test
    void aCheckoutThatExpiredWhileItsEventWasLostIsClearedAndAnOpenOneIsLeft() throws Exception {
        UUID user = payer(true);
        UUID expired = pending(syncedLicense(999), user, 999, 999, null);
        UUID open = pending(syncedLicense(999), payer(true), 999, 999, null);
        jdbc.update("update payment_subscriptions set stripe_checkout_session_id = 'cs_gone' "
                + "where license_subscription_id = ?", expired);
        jdbc.update("update payment_subscriptions set stripe_checkout_session_id = 'cs_open' "
                + "where license_subscription_id = ?", open);
        given(stripeCheckout.retrieve("cs_gone")).willReturn(new StripeCheckoutSessionState("cs_gone", "expired",
                "unpaid", null, null));
        given(stripeCheckout.retrieve("cs_open")).willReturn(new StripeCheckoutSessionState("cs_open", "open",
                "unpaid", null, null));

        reconcileEverythingDue();

        assertThat(billing(expired, "stripe_checkout_session_id", String.class)).isNull();
        assertThat(billing(open, "stripe_checkout_session_id", String.class)).isEqualTo("cs_open");
    }

    @Test
    void aSubscribersSecondCheckoutReusesTheirStripeCustomer() throws Exception {
        given(stripeCheckout.createSubscriptionSession(any())).willAnswer(i -> new StripeCheckoutSession(
                "cs_" + UUID.randomUUID(), "https://checkout.stripe.com/pay", Instant.now().plus(Duration.ofHours(23))));
        UUID user = payer(true);
        UUID first = pending(syncedLicense(999), user, 999, 999, null);
        UUID second = pending(syncedLicense(1999), user, 1999, 1999, null);

        send(post(CHECKOUT), Map.of("subscriptionId", first), Caller.CUSTOMER.request(user)).andExpect(status().isOk());
        send(post(CHECKOUT), Map.of("subscriptionId", second), Caller.CUSTOMER.request(user)).andExpect(status().isOk());

        then(stripeCustomers).should(times(1)).create(any(), any(), any(), any());
        then(stripeCheckout).should(times(2)).createSubscriptionSession(any());
    }

    @Test
    void aRefundThatFailedOrWasCanceledGivesNothingBack() throws Exception {
        UUID user = payer(true);
        String stripeSubscription = paidThroughCheckout(pending(syncedLicense(999), user, 999, 999, null));
        UUID invoice = paidInvoice(stripeSubscription, "subscription_create");
        String paymentIntent = jdbc.queryForObject("select stripe_payment_intent_id from payment_invoices where id = ?",
                String.class, invoice);

        for (String status : new String[] {"failed", "canceled", "pending"}) {
            webhook(event("refund.created", """
                    {"id": "re_%s_%s", "object": "refund", "payment_intent": "%s", "amount": 100, "currency": "eur",
                     "status": "%s", "created": %d, "metadata": {}}"""
                    .formatted(status, invoice, paymentIntent, status, Instant.now().getEpochSecond())))
                    .andExpect(status().isOk());
        }
        stripeEventJob.processDue();

        assertThat(jdbc.queryForObject("select amount_refunded_minor from payment_invoices where id = ?", Long.class,
                invoice)).as("only the pending refund counts").isEqualTo(100);
    }

    @Test
    void aSyncFailingForLongWaitsAtMostAnHourAndKeepsTheStartOfALongError() {
        UUID licenseId = syncedLicense(999);
        jdbc.update("update payment_license_products set sync_attempts = 30 where license_id = ?", licenseId);
        Instant now = Instant.now();

        catalogSync.recordFailure(licenseId, new IllegalStateException("x".repeat(5000)), now);

        assertThat(jdbc.queryForObject("select length(last_sync_error) from payment_license_products "
                + "where license_id = ?", Integer.class, licenseId)).isEqualTo(4000);
        assertThat(jdbc.queryForObject("select next_sync_at from payment_license_products where license_id = ?",
                Timestamp.class, licenseId).toInstant()).isCloseTo(now.plus(Duration.ofHours(1)),
                within(1, java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void aStripeEventFailingForLongWaitsAtMostAnHourAndOneAlreadyDoneIsLeftAlone() throws Exception {
        UUID failing = storedEvent();
        UUID done = storedEvent();
        jdbc.update("update payment_stripe_events set attempts = 7 where id = ?", failing);
        jdbc.update("update payment_stripe_events set status = 'PROCESSED' where id = ?", done);
        Instant now = Instant.now();

        stripeEvents.recordFailure(failing, new IllegalStateException("y".repeat(5000)), now);
        stripeEvents.recordFailure(done, new IllegalStateException("late"), now);

        assertThat(jdbc.queryForObject("select length(last_error) from payment_stripe_events where id = ?",
                Integer.class, failing)).isEqualTo(4000);
        assertThat(jdbc.queryForObject("select next_attempt_at from payment_stripe_events where id = ?",
                Timestamp.class, failing).toInstant()).isCloseTo(now.plus(Duration.ofHours(1)),
                within(1, java.time.temporal.ChronoUnit.SECONDS));
        assertThat(jdbc.queryForObject("select attempts from payment_stripe_events where id = ?", Integer.class,
                done)).isZero();
    }

    private UUID groupAdmin() {
        UUID adminUser = payer(true);
        groupEvents.onMembershipCreated(new GroupMembershipCreatedEvent(UUID.randomUUID(), group, adminUser, "ADMIN"));
        return adminUser;
    }

    private UUID groupPending(UUID adminUser) {
        UUID subscriptionId = UUID.randomUUID();
        Instant now = Instant.now();
        licenseEvents.onSubscriptionPending(new LicenseSubscriptionPendingEvent(subscriptionId, syncedLicense(2999),
                LicenseTarget.GROUP, group, adminUser, BillingInterval.MONTH, 2999, 2999, "EUR", null, now,
                now.plus(Duration.ofHours(48))));
        return subscriptionId;
    }

    private UUID paidInvoice(String stripeSubscription, String billingReason) throws Exception {
        String invoiceId = "in_" + UUID.randomUUID();
        given(stripeInvoices.paidPaymentIntentId(invoiceId)).willReturn(Optional.of("pi_" + UUID.randomUUID()));
        webhook(event("invoice.paid", invoice(invoiceId, stripeSubscription, billingReason, "paid", 2999)))
                .andExpect(status().isOk());
        stripeEventJob.processDue();
        return jdbc.queryForObject("select id from payment_invoices where stripe_invoice_id = ?", UUID.class, invoiceId);
    }

    private UUID storedEvent() throws Exception {
        String payload = event("customer.created", "{\"id\": \"cus_%s\", \"object\": \"customer\"}"
                .formatted(UUID.randomUUID()));
        webhook(payload).andExpect(status().isOk());
        String stripeEventId = json.readTree(payload).path("id").asText();
        return jdbc.queryForObject("select id from payment_stripe_events where stripe_event_id = ?", UUID.class,
                stripeEventId);
    }

    private java.util.List<SendEmailRequest> emails() {
        return published.published().stream().map(Published::payload).filter(SendEmailRequest.class::isInstance)
                .map(SendEmailRequest.class::cast).toList();
    }

    private void reconcileEverythingDue() {
        Instant cutoff = Instant.now().plus(Duration.ofSeconds(1));
        for (int i = 0; i < 500; i++) {
            try {
                if (reconciliation.reconcileNext(Instant.now(), cutoff).isEmpty()) {
                    return;
                }
            } catch (com.triphuddle.api.payment.service.ReconciliationException e) {
                reconciliation.recordFailure(e.getLicenseSubscriptionId(), e.getCause(), Instant.now());
            }
        }
    }
}
