package com.triphuddle.api.payment.service;

import com.triphuddle.amqp.license.LicenseSubscriptionActivatedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionEndedEvent;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.payment.entity.BillingSubscription;
import com.triphuddle.api.payment.entity.BillingSubscriptionStatus;
import com.triphuddle.api.payment.messaging.PaymentEventPublisher;
import com.triphuddle.api.payment.repository.BillingSubscriptionRepository;
import com.triphuddle.stripe.checkout.StripeCheckoutClient;
import com.triphuddle.stripe.subscription.StripeSubscriptionClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class BillingServicesTest {

    private final BillingSubscriptionRepository repository = mock(BillingSubscriptionRepository.class);
    private final StripeSubscriptionClient stripeSubscriptions = mock(StripeSubscriptionClient.class);
    private final StripeCheckoutClient checkout = mock(StripeCheckoutClient.class);
    private final PaymentEventPublisher publisher = mock(PaymentEventPublisher.class);
    private final UUID payer = UUID.randomUUID();

    private BillingSubscription given(BillingSubscription subscription) {
        org.mockito.BDDMockito.given(repository.lockByLicenseSubscriptionId(subscription.getLicenseSubscriptionId()))
                .willReturn(Optional.of(subscription));
        org.mockito.BDDMockito.given(repository.findByLicenseSubscriptionId(subscription.getLicenseSubscriptionId()))
                .willReturn(Optional.of(subscription));
        if (subscription.getStripeSubscriptionId() != null) {
            org.mockito.BDDMockito.given(repository.lockByStripeSubscriptionId(subscription.getStripeSubscriptionId()))
                    .willReturn(Optional.of(subscription));
        }
        return subscription;
    }

    @Nested
    class Subscriptions {

        private final BillingSubscriptionService service = new BillingSubscriptionService(repository, checkout,
                stripeSubscriptions, publisher);

        @Test
        void aGrantOrAnAlreadySettledSubscriptionIgnoresAnActivation() {
            BillingSubscription ended = given(PaymentFixtures.pending(payer));
            ended.setStatus(BillingSubscriptionStatus.ENDED);

            service.onActivated(activated(ended.getLicenseSubscriptionId(), true), Instant.now());
            service.onActivated(activated(ended.getLicenseSubscriptionId(), false), Instant.now());

            assertThat(ended.getStatus()).isEqualTo(BillingSubscriptionStatus.ENDED);
            then(repository).should(never()).save(any());
        }

        @Test
        void anActivationThatOvertookThePendingEventIsRecordedSoPendingCannotUndoIt() {
            UUID id = UUID.randomUUID();

            service.onActivated(activated(id, false), Instant.now());

            then(repository).should().save(org.mockito.ArgumentMatchers.argThat(s ->
                    s.getStatus() == BillingSubscriptionStatus.ACTIVE && s.getLicenseSubscriptionId().equals(id)));
        }

        @Test
        void anEndOfASubscriptionNeverSeenIsSkippedUnlessItEndedWhilePending() {
            UUID unknown = UUID.randomUUID();

            service.onEnded(ended(unknown, "EXPIRED", "PERIOD_ENDED"));
            then(repository).should(never()).save(any());

            service.onEnded(ended(unknown, "ENDED", "PENDING_TIMEOUT"));
            then(repository).should().save(org.mockito.ArgumentMatchers.argThat(s ->
                    s.getStatus() == BillingSubscriptionStatus.ENDED));
        }

        @Test
        void anEndStopsAStripeSubscriptionStillHeldButNotOneFlaggedForRefund() {
            BillingSubscription switched = given(PaymentFixtures.active(payer, "sub_1"));
            BillingSubscription refunded = given(PaymentFixtures.active(payer, "sub_2"));
            refunded.setRefundDueAt(Instant.now());

            service.onEnded(ended(switched.getLicenseSubscriptionId(), "ENDED", "SWITCHED"));
            service.onEnded(ended(refunded.getLicenseSubscriptionId(), "ENDED", "SWITCHED"));

            then(stripeSubscriptions).should().cancelNow("sub_1");
            then(stripeSubscriptions).should(never()).cancelNow("sub_2");
        }

        @Test
        void aPaidCheckoutBeforeThePendingEventIsRetriedAndARepeatIsIgnored() {
            assertThatThrownBy(() -> service.onCheckoutPaid(UUID.randomUUID(), "cs", "cus", "sub"))
                    .hasMessageContaining("not recorded yet");

            BillingSubscription paid = given(PaymentFixtures.active(payer, "sub_3"));
            service.onCheckoutPaid(paid.getLicenseSubscriptionId(), "cs", "cus", "sub_3");
            then(stripeSubscriptions).shouldHaveNoInteractions();
        }

        @Test
        void anExpiredCheckoutClearsOnlyItsOwnSession() {
            BillingSubscription subscription = given(PaymentFixtures.pending(payer));
            subscription.setStripeCheckoutSessionId("cs_current");
            subscription.setStripeCheckoutUrl("https://c");

            service.onCheckoutExpired(subscription.getLicenseSubscriptionId(), "cs_older");
            assertThat(subscription.getStripeCheckoutSessionId()).isEqualTo("cs_current");
            service.onCheckoutExpired(subscription.getLicenseSubscriptionId(), "cs_current");
            assertThat(subscription.getStripeCheckoutSessionId()).isNull();
            assertThat(subscription.getStripeCheckoutUrl()).isNull();
        }

        @Test
        void aRefusalForAnotherStripeSubscriptionChangesNothing() {
            BillingSubscription subscription = given(PaymentFixtures.active(payer, "sub_4"));

            service.onActivationRefused(subscription.getLicenseSubscriptionId(), "sub_other", "ENDED");

            assertThat(subscription.getRefundDueAt()).isNull();
            then(stripeSubscriptions).shouldHaveNoInteractions();
        }

        private LicenseSubscriptionActivatedEvent activated(UUID id, boolean isGranted) {
            return new LicenseSubscriptionActivatedEvent(id, UUID.randomUUID(), "P", "Explorer", LicenseTarget.USER,
                    payer, payer, Instant.now(), true, isGranted, 999, "EUR", Map.of());
        }

        private LicenseSubscriptionEndedEvent ended(UUID id, String status, String reason) {
            return new LicenseSubscriptionEndedEvent(id, UUID.randomUUID(), "Explorer", LicenseTarget.USER, payer,
                    payer, status, reason, Instant.now());
        }
    }

    @Nested
    class Stripe {

        private final PaymentEmailService emails = mock(PaymentEmailService.class);
        private final InvoiceService invoices = mock(InvoiceService.class);
        private final StripeBillingService service = new StripeBillingService(repository, stripeSubscriptions,
                publisher, emails, invoices);

        private StripeInvoice invoice(String reason, String stripeSubscriptionId, long amount) {
            return new StripeInvoice("in_1", "N-1", stripeSubscriptionId, reason, "paid", amount, 0, amount, amount,
                    "eur", "https://i", "https://i.pdf", Instant.now(), Instant.now(), null);
        }

        @Test
        void aFreeInvoiceIsRecordedWithoutAnEmailAndAFirstOneWithoutARenewal() {
            BillingSubscription subscription = given(PaymentFixtures.active(payer, "sub_5"));

            service.onInvoicePaid(invoice("subscription_create", "sub_5", 0));

            then(invoices).should().record(any(), org.mockito.ArgumentMatchers.eq(subscription));
            then(emails).shouldHaveNoInteractions();
            then(publisher).shouldHaveNoInteractions();
        }

        @Test
        void anInvoiceOfAnUnknownSubscriptionIsSkippedUnlessItIsAFirstOneWhichWaitsForCheckout() {
            service.onInvoicePaid(invoice("subscription_cycle", "sub_unknown", 999));
            then(invoices).shouldHaveNoInteractions();

            assertThatThrownBy(() -> service.onInvoicePaid(invoice("subscription_create", "sub_unknown", 999)))
                    .hasMessageContaining("before its checkout");
        }

        @Test
        void aFailedFirstChargeIsLeftToCheckoutAndAFailedRenewalOfAnUnknownSubscriptionToNobody() {
            service.onInvoicePaymentFailed(invoice("subscription_create", "sub_5", 999));
            service.onInvoicePaymentFailed(invoice("subscription_cycle", "sub_unknown", 999));

            then(publisher).shouldHaveNoInteractions();
            then(emails).shouldHaveNoInteractions();
        }

        @Test
        void aChangedInvoiceIsRecordedButAFirstOneThatWasNeverPaidIsNot() {
            BillingSubscription subscription = given(PaymentFixtures.active(payer, "sub_6"));

            service.onInvoiceChanged(invoice("subscription_create", "sub_never", 999));
            service.onInvoiceChanged(invoice("manual", "sub_6", 999));

            then(invoices).should(org.mockito.Mockito.times(1)).record(any(),
                    org.mockito.ArgumentMatchers.eq(subscription));
        }

        @Test
        void aDeletionOfAFlaggedOrEndedSubscriptionIsNotPassedOn() {
            BillingSubscription flagged = given(PaymentFixtures.active(payer, "sub_7"));
            flagged.setRefundDueAt(Instant.now());
            BillingSubscription ended = given(PaymentFixtures.active(payer, "sub_8"));
            ended.setStatus(BillingSubscriptionStatus.ENDED);

            service.onStripeSubscriptionDeleted("sub_7", Instant.now(), "x");
            service.onStripeSubscriptionDeleted("sub_8", Instant.now(), "x");
            service.onStripeSubscriptionUpdated("sub_8", true);

            then(publisher).shouldHaveNoInteractions();
        }

        @Test
        void aRenewalRequestOnlyReachesStripeWhenItChangesSomething() {
            BillingSubscription subscription = given(PaymentFixtures.active(payer, "sub_9"));

            service.onRenewalRequested(subscription.getLicenseSubscriptionId(), true);
            then(stripeSubscriptions).should(never()).setCancelAtPeriodEnd(any(), anyBoolean());

            service.onRenewalRequested(subscription.getLicenseSubscriptionId(), false);
            then(stripeSubscriptions).should().setCancelAtPeriodEnd("sub_9", true);
            assertThat(subscription.isCancelAtPeriodEnd()).isTrue();
        }

        @Test
        void aRemovedUsersPaidSubscriptionsStopRenewingOnce() {
            BillingSubscription renewing = given(PaymentFixtures.active(payer, "sub_10"));
            BillingSubscription stopping = given(PaymentFixtures.active(payer, "sub_11"));
            stopping.setCancelAtPeriodEnd(true);
            org.mockito.BDDMockito.given(repository.findBySubscriberTypeAndSubscriberIdAndStatusAndStripeSubscriptionIdIsNotNull(
                    LicenseTarget.USER, payer, BillingSubscriptionStatus.ACTIVE)).willReturn(List.of(renewing, stopping));

            service.onUserRemoved(payer);

            then(stripeSubscriptions).should().setCancelAtPeriodEnd("sub_10", true);
            then(stripeSubscriptions).should(never()).setCancelAtPeriodEnd("sub_11", true);
            then(publisher).should().renewalChanged(renewing.getLicenseSubscriptionId(), false);
        }
    }
}
