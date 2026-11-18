package com.triphuddle.api.payment.api;

import com.triphuddle.amqp.license.LicenseSubscriptionActivationRefusedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionCanceledEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionEndedEvent;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.amqp.notification.NotificationEmailType;
import com.triphuddle.amqp.notification.SendEmailRequest;
import com.triphuddle.amqp.payment.PaymentSubscriptionEndedEvent;
import com.triphuddle.amqp.payment.PaymentSubscriptionPastDueEvent;
import com.triphuddle.amqp.payment.PaymentSubscriptionRenewalChangedEvent;
import com.triphuddle.amqp.payment.PaymentSubscriptionRenewedEvent;
import com.triphuddle.stripe.subscription.StripeSubscriptionPeriod;
import com.triphuddle.testsupport.messaging.RecordingMessageProducer.Published;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PAY-3, PAY-4 and PAY-8: what Stripe reports after the first payment, what api-license asks of Stripe, and who is
 * emailed about it.
 */
class BillingFlowsIT extends PaymentApiSupport {

    @Test
    void aRenewalIsRecordedPassedToApiLicenseAndEmailedToTheVerifiedPayer() throws Exception {
        UUID user = payer(true);
        UUID subscription = pending(syncedLicense(999), user, 999, 999, null);
        String stripeSubscriptionId = paidThroughCheckout(subscription);
        Instant nextEnd = periodEnd.plus(Duration.ofDays(30));
        given(stripeSubscriptions.retrieve(stripeSubscriptionId)).willReturn(new StripeSubscriptionPeriod(
                stripeSubscriptionId, "active", periodEnd, nextEnd, false, Map.of()));
        given(stripeInvoices.paidPaymentIntentId("in_renew_" + subscription)).willReturn(Optional.of("pi_1"));
        published.reset();

        webhook(event("invoice.paid", invoice("in_renew_" + subscription, stripeSubscriptionId, "subscription_cycle",
                "paid", 999))).andExpect(status().isOk());
        stripeEventJob.processDue();

        assertThat(published.published()).extracting(Published::payload)
                .contains(new PaymentSubscriptionRenewedEvent(subscription, stripeSubscriptionId, periodEnd, nextEnd))
                .anySatisfy(p -> assertThat(p).isInstanceOfSatisfying(SendEmailRequest.class, email -> {
                    assertThat(email.type()).isEqualTo(NotificationEmailType.LICENSE_PAYMENT_SUCCEEDED);
                    assertThat(email.recipientEmail()).isEqualTo("payer-" + user + "@example.com");
                    assertThat(email.templateData()).containsEntry("amount", "9.99 EUR");
                }));
        assertThat(jdbc.queryForMap("select amount_paid_minor, tax_minor, stripe_payment_intent_id, currency from "
                + "payment_invoices where stripe_invoice_id = ?", "in_renew_" + subscription))
                .containsEntry("amount_paid_minor", 999L).containsEntry("tax_minor", 160L)
                .containsEntry("stripe_payment_intent_id", "pi_1").containsEntry("currency", "EUR");
    }

    @Test
    void aFailedRenewalMarksItPastDueAndAnUnverifiedPayerGetsNoEmail() throws Exception {
        UUID user = payer(false);
        UUID subscription = pending(syncedLicense(999), user, 999, 999, null);
        String stripeSubscriptionId = paidThroughCheckout(subscription);
        published.reset();

        webhook(event("invoice.payment_failed", invoice("in_fail_" + subscription, stripeSubscriptionId,
                "subscription_cycle", "open", 999))).andExpect(status().isOk());
        stripeEventJob.processDue();

        assertThat(published.published()).extracting(Published::payload)
                .anySatisfy(p -> assertThat(p).isInstanceOf(PaymentSubscriptionPastDueEvent.class))
                .noneSatisfy(p -> assertThat(p).isInstanceOf(SendEmailRequest.class));
    }

    @Test
    void stripesRenewalSwitchAndEndArePassedOnOnce() throws Exception {
        UUID subscription = activePaid();
        String stripeSubscriptionId = billing(subscription, "stripe_subscription_id", String.class);
        published.reset();
        String canceledAtPeriodEnd = """
                {"id": "%s", "object": "subscription", "status": "active", "cancel_at_period_end": true}"""
                .formatted(stripeSubscriptionId);

        webhook(event("customer.subscription.updated", canceledAtPeriodEnd)).andExpect(status().isOk());
        webhook(event("customer.subscription.updated", canceledAtPeriodEnd)).andExpect(status().isOk());
        webhook(event("customer.subscription.deleted", """
                {"id": "%s", "object": "subscription", "status": "canceled", "ended_at": %d,
                 "cancellation_details": {"reason": "cancellation_requested"}}"""
                .formatted(stripeSubscriptionId, periodEnd.getEpochSecond()))).andExpect(status().isOk());
        stripeEventJob.processDue();

        assertThat(published.published()).extracting(Published::payload)
                .containsOnlyOnce(new PaymentSubscriptionRenewalChangedEvent(subscription, false))
                .contains(new PaymentSubscriptionEndedEvent(subscription, stripeSubscriptionId, periodEnd,
                        "cancellation_requested"));
    }

    @Test
    void cancelingInApiLicenseStopsTheRenewalAtStripe() throws Exception {
        UUID subscription = activePaid();
        String stripeSubscriptionId = billing(subscription, "stripe_subscription_id", String.class);

        licenseEvents.onSubscriptionCanceled(new LicenseSubscriptionCanceledEvent(subscription, UUID.randomUUID(),
                "Explorer", LicenseTarget.USER, UUID.randomUUID(), null, UUID.randomUUID(), periodEnd));
        licenseEvents.onSubscriptionCanceled(new LicenseSubscriptionCanceledEvent(subscription, UUID.randomUUID(),
                "Explorer", LicenseTarget.USER, UUID.randomUUID(), null, UUID.randomUUID(), periodEnd));

        then(stripeSubscriptions).should(org.mockito.Mockito.times(1)).setCancelAtPeriodEnd(stripeSubscriptionId,
                true);
        assertThat(billing(subscription, "is_cancel_at_period_end", Boolean.class)).isTrue();
    }

    @Test
    void aPaymentForASubscriptionThatEndedStopsStripeAndIsFlaggedForRefund() throws Exception {
        UUID user = payer(true);
        UUID subscription = pending(syncedLicense(999), user, 999, 999, null);
        licenseEvents.onSubscriptionEnded(new LicenseSubscriptionEndedEvent(subscription, UUID.randomUUID(),
                "Explorer", LicenseTarget.USER, user, user, "ENDED", "PENDING_TIMEOUT", Instant.now()));

        String stripeSubscriptionId = paidThroughCheckout(subscription);

        then(stripeSubscriptions).should().cancelNow(stripeSubscriptionId);
        assertThat(billing(subscription, "refund_due_at", java.sql.Timestamp.class)).isNotNull();
    }

    @Test
    void apiLicenseRefusingAnActivationAlsoStopsStripeAndFlagsTheRefund() throws Exception {
        UUID user = payer(true);
        UUID subscription = pending(syncedLicense(999), user, 999, 999, null);
        String stripeSubscriptionId = paidThroughCheckout(subscription);

        licenseEvents.onActivationRefused(new LicenseSubscriptionActivationRefusedEvent(subscription,
                stripeSubscriptionId, "ENDED"));

        then(stripeSubscriptions).should().cancelNow(stripeSubscriptionId);
        assertThat(billing(subscription, "refund_due_at", java.sql.Timestamp.class)).isNotNull();
    }

    @Test
    void anOpenCheckoutIsExpiredWhenStaffActivateOrTheSubscriptionEnds() throws Exception {
        UUID user = payer(true);
        UUID subscription = pending(syncedLicense(999), user, 999, 999, null);
        jdbc.update("update payment_subscriptions set stripe_checkout_session_id = 'cs_open' "
                + "where license_subscription_id = ?", subscription);

        licenseEvents.onSubscriptionActivated(new com.triphuddle.amqp.license.LicenseSubscriptionActivatedEvent(
                subscription, UUID.randomUUID(), "PLAN", "Explorer", LicenseTarget.USER, user, user, periodEnd, false,
                false, 999, "EUR", Map.of()));

        then(stripeCheckout).should().expireIfOpen("cs_open");
        assertThat(billing(subscription, "status", String.class)).isEqualTo("ACTIVE");
        then(stripeSubscriptions).should(never()).cancelNow(org.mockito.ArgumentMatchers.anyString());
    }

    private UUID activePaid() throws Exception {
        UUID user = payer(true);
        UUID subscription = pending(syncedLicense(999), user, 999, 999, null);
        paidThroughCheckout(subscription);
        licenseEvents.onSubscriptionActivated(new com.triphuddle.amqp.license.LicenseSubscriptionActivatedEvent(
                subscription, UUID.randomUUID(), "PLAN", "Explorer", LicenseTarget.USER, user, user, periodEnd, true,
                false, 999, "EUR", Map.of()));
        return subscription;
    }
}
