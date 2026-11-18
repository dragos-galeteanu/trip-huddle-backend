package com.triphuddle.api.payment.api;

import com.triphuddle.amqp.license.BillingInterval;
import com.triphuddle.amqp.license.LicenseCatalogChangedEvent;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.amqp.payment.PaymentSubscriptionEndedEvent;
import com.triphuddle.amqp.payment.PaymentSubscriptionPastDueEvent;
import com.triphuddle.amqp.payment.PaymentSubscriptionRenewalChangedEvent;
import com.triphuddle.api.payment.service.ReconciliationService;
import com.triphuddle.stripe.catalog.StripePriceSpec;
import com.triphuddle.stripe.catalog.StripeProductSpec;
import com.triphuddle.stripe.checkout.StripeCheckoutSessionState;
import com.stripe.exception.ApiConnectionException;
import com.triphuddle.stripe.exception.StripeIntegrationException;
import com.triphuddle.stripe.subscription.StripeSubscriptionPeriod;
import com.triphuddle.testsupport.messaging.RecordingMessageProducer.Published;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

/**
 * PAY-6: the daily comparison with Stripe repairs what a lost webhook missed. PAY-7: published licenses reach Stripe
 * as products with tax-inclusive prices.
 */
class ReconciliationAndCatalogIT extends PaymentApiSupport {

    @Autowired
    ReconciliationService reconciliation;

    @Test
    void aMissedEndPastDueOrRenewalSwitchIsRepaired() throws Exception {
        UUID ended = paidSubscription();
        UUID pastDue = paidSubscription();
        UUID canceled = paidSubscription();
        stripeSays(ended, "canceled", false);
        stripeSays(pastDue, "past_due", false);
        stripeSays(canceled, "active", true);
        published.reset();

        reconcileEverythingDue();

        assertThat(published.published()).extracting(Published::payload)
                .anySatisfy(p -> assertThat(p).isInstanceOfSatisfying(PaymentSubscriptionEndedEvent.class,
                        e -> assertThat(e.licenseSubscriptionId()).isEqualTo(ended)))
                .anySatisfy(p -> assertThat(p).isInstanceOfSatisfying(PaymentSubscriptionPastDueEvent.class,
                        e -> assertThat(e.licenseSubscriptionId()).isEqualTo(pastDue)))
                .contains(new PaymentSubscriptionRenewalChangedEvent(canceled, false));
        assertThat(billing(canceled, "reconciled_at", java.sql.Timestamp.class)).isNotNull();
    }

    @Test
    void aCheckoutPaidWhileItsEventWasLostIsActivated() throws Exception {
        UUID user = payer(true);
        UUID subscription = pending(syncedLicense(999), user, 999, 999, null);
        jdbc.update("update payment_subscriptions set stripe_checkout_session_id = 'cs_lost' "
                + "where license_subscription_id = ?", subscription);
        given(stripeCheckout.retrieve("cs_lost")).willReturn(new StripeCheckoutSessionState("cs_lost", "complete",
                "paid", "cus_9", "sub_lost"));
        given(stripeSubscriptions.retrieve("sub_lost")).willReturn(new StripeSubscriptionPeriod("sub_lost", "active",
                periodStart, periodEnd, false, Map.of()));

        reconcileEverythingDue();

        assertThat(billing(subscription, "stripe_subscription_id", String.class)).isEqualTo("sub_lost");
    }

    @Test
    void aStripeFailureMarksTheSubscriptionCheckedAndMovesOn() throws Exception {
        UUID subscription = paidSubscription();
        willThrow(new StripeIntegrationException("down", new ApiConnectionException("Stripe is down"))).given(stripeSubscriptions)
                .retrieve(billing(subscription, "stripe_subscription_id", String.class));

        reconcileEverythingDue();

        assertThat(billing(subscription, "reconciled_at", java.sql.Timestamp.class)).isNotNull();
    }

    @Test
    void aPublishedLicenseBecomesAProductWithItsTaxInclusivePriceAndLaterChangesFollow() {
        UUID licenseId = UUID.randomUUID();
        Instant changed = Instant.now();
        licenseEvents.onCatalogChanged(new LicenseCatalogChangedEvent(licenseId, "PLAN_Y", "Yearly", "All year",
                LicenseTarget.GROUP, "PUBLISHED", 9999, "EUR", BillingInterval.YEAR, changed));
        // A late, older event changes nothing.
        licenseEvents.onCatalogChanged(new LicenseCatalogChangedEvent(licenseId, "PLAN_Y", "Old name", null,
                LicenseTarget.GROUP, "PUBLISHED", 1, "EUR", BillingInterval.YEAR, changed.minusSeconds(60)));

        while (catalogSync.syncNext(Instant.now().plusSeconds(1)).isPresent()) {
            // earlier tests' products may be due as well
        }

        ArgumentCaptor<StripeProductSpec> product = ArgumentCaptor.forClass(StripeProductSpec.class);
        then(stripeCatalog).should(org.mockito.Mockito.atLeastOnce()).upsertProduct(product.capture());
        assertThat(product.getAllValues()).anySatisfy(spec -> {
            assertThat(spec.id()).isEqualTo("license_" + licenseId);
            assertThat(spec.name()).isEqualTo("Yearly");
            assertThat(spec.isActive()).isTrue();
        });
        ArgumentCaptor<StripePriceSpec> price = ArgumentCaptor.forClass(StripePriceSpec.class);
        then(stripeCatalog).should(org.mockito.Mockito.atLeastOnce()).ensurePrice(price.capture());
        assertThat(price.getAllValues()).anySatisfy(spec -> {
            assertThat(spec.lookupKey()).isEqualTo("license_%s_year_9999_eur".formatted(licenseId));
            assertThat(spec.isYearly()).isTrue();
        });
    }

    @Test
    void aFailedSyncIsRetriedLaterAndNeverGivenUp() {
        UUID licenseId = UUID.randomUUID();
        willThrow(new StripeIntegrationException("down", new ApiConnectionException("Stripe is down"))).given(stripeCatalog).upsertProduct(any());
        licenseEvents.onCatalogChanged(new LicenseCatalogChangedEvent(licenseId, "PLAN_F", "Failing", null,
                LicenseTarget.USER, "PUBLISHED", 500, "EUR", BillingInterval.MONTH, Instant.now()));

        new com.triphuddle.api.payment.job.CatalogSyncJob(catalogSync).syncDue();

        assertThat(jdbc.queryForObject("select sync_attempts from payment_license_products where license_id = ?",
                Integer.class, licenseId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select next_sync_at > now() from payment_license_products where license_id = ?",
                Boolean.class, licenseId)).isTrue();
    }

    private UUID paidSubscription() throws Exception {
        UUID user = payer(true);
        UUID subscription = pending(syncedLicense(999), user, 999, 999, null);
        paidThroughCheckout(subscription);
        licenseEvents.onSubscriptionActivated(new com.triphuddle.amqp.license.LicenseSubscriptionActivatedEvent(
                subscription, UUID.randomUUID(), "PLAN", "Explorer", LicenseTarget.USER, user, user, periodEnd, true,
                false, 999, "EUR", Map.of()));
        return subscription;
    }

    private void stripeSays(UUID subscription, String status, boolean isCancelAtPeriodEnd) {
        String id = billing(subscription, "stripe_subscription_id", String.class);
        given(stripeSubscriptions.retrieve(id)).willReturn(new StripeSubscriptionPeriod(id, status, periodStart,
                periodEnd, isCancelAtPeriodEnd, Map.of()));
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
