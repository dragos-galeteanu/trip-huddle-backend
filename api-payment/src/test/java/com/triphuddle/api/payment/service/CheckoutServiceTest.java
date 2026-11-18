package com.triphuddle.api.payment.service;

import com.stripe.exception.CardException;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.payment.config.PaymentProperties;
import com.triphuddle.api.payment.dto.CheckoutResponse;
import com.triphuddle.api.payment.entity.BillingSubscription;
import com.triphuddle.api.payment.entity.BillingSubscriptionStatus;
import com.triphuddle.api.payment.entity.LicenseProduct;
import com.triphuddle.api.payment.messaging.PaymentEventPublisher;
import com.triphuddle.api.payment.repository.BillingSubscriptionRepository;
import com.triphuddle.api.payment.repository.LicenseProductRepository;
import com.triphuddle.api.payment.repository.UserContactRepository;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.stripe.checkout.StripeCheckoutClient;
import com.triphuddle.stripe.checkout.StripeCheckoutSession;
import com.triphuddle.stripe.checkout.StripeCheckoutSpec;
import com.triphuddle.stripe.exception.StripeIntegrationException;
import com.triphuddle.stripe.subscription.StripeSubscriptionClient;
import com.triphuddle.stripe.subscription.StripeSubscriptionPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class CheckoutServiceTest {

    private final BillingSubscriptionRepository subscriptions = mock(BillingSubscriptionRepository.class);
    private final LicenseProductRepository products = mock(LicenseProductRepository.class);
    private final UserContactRepository contacts = mock(UserContactRepository.class);
    private final CatalogSyncService catalog = mock(CatalogSyncService.class);
    private final CustomerService customers = mock(CustomerService.class);
    private final StripeCheckoutClient checkout = mock(StripeCheckoutClient.class);
    private final StripeSubscriptionClient stripeSubscriptions = mock(StripeSubscriptionClient.class);
    private final PaymentEventPublisher publisher = mock(PaymentEventPublisher.class);
    private final CheckoutService service = new CheckoutService(subscriptions, products, contacts, catalog, customers,
            checkout, stripeSubscriptions, publisher, new PaymentProperties(1, 10, 1, null,
            "https://app/ok/{subscriptionId}", "https://app/cancel/{subscriptionId}", "https://app/s", 1, 24));

    private final UUID payer = UUID.randomUUID();
    private BillingSubscription subscription;

    @BeforeEach
    void aPendingSubscriptionReadyToPay() {
        subscription = PaymentFixtures.pending(payer);
        given(subscriptions.lockByLicenseSubscriptionId(subscription.getLicenseSubscriptionId()))
                .willReturn(Optional.of(subscription));
        given(products.lockByLicenseId(subscription.getLicenseId()))
                .willReturn(Optional.of(PaymentFixtures.product(subscription.getLicenseId(), true)));
        given(contacts.findByUserId(payer)).willReturn(Optional.of(PaymentFixtures.contact(payer, true)));
        given(customers.ensureCustomer(any(), any(), any())).willReturn("cus_1");
        given(catalog.ensurePrice(any(), anyLong())).willReturn(PaymentFixtures.price(subscription.getLicenseId()));
        given(checkout.createSubscriptionSession(any())).willReturn(new StripeCheckoutSession("cs_1",
                "https://checkout/1", Instant.now().plus(Duration.ofHours(20))));
    }

    private CheckoutResponse checkOut() {
        return service.checkout(subscription.getLicenseSubscriptionId(), payer);
    }

    @Nested
    class Refusals {

        @Test
        void anUnknownOrDetaillessSubscriptionIsNotFound() {
            subscription.setLicenseId(null);

            assertThatThrownBy(CheckoutServiceTest.this::checkOut).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> service.checkout(UUID.randomUUID(), payer))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void aSubscriptionNoLongerPendingOrPastItsTimeIsRefused() {
            subscription.setStatus(BillingSubscriptionStatus.ENDED);
            assertThatThrownBy(CheckoutServiceTest.this::checkOut).isInstanceOf(IllegalStateException.class);

            subscription.setStatus(BillingSubscriptionStatus.PENDING);
            subscription.setPendingExpiresAt(Instant.now().minusSeconds(1));
            assertThatThrownBy(CheckoutServiceTest.this::checkOut).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void aLicenseNotSyncedOrNoLongerOfferedIsRefused() {
            LicenseProduct unsynced = PaymentFixtures.product(subscription.getLicenseId(), true);
            unsynced.setSyncedAt(null);
            given(products.lockByLicenseId(subscription.getLicenseId())).willReturn(Optional.of(unsynced));
            assertThatThrownBy(CheckoutServiceTest.this::checkOut).hasMessageContaining("being set up");

            given(products.lockByLicenseId(subscription.getLicenseId()))
                    .willReturn(Optional.of(PaymentFixtures.product(subscription.getLicenseId(), false)));
            assertThatThrownBy(CheckoutServiceTest.this::checkOut).hasMessageContaining("no longer offered");
        }

        @Test
        void aPayerWhoseDetailsHaveNotArrivedOrWhoIsDisabledIsRefused() {
            var noEmail = PaymentFixtures.contact(payer, true);
            noEmail.setEmail(null);
            given(contacts.findByUserId(payer)).willReturn(Optional.of(noEmail));
            assertThatThrownBy(CheckoutServiceTest.this::checkOut).hasMessageContaining("account details");

            var disabled = PaymentFixtures.contact(payer, true);
            disabled.setDisabledAt(Instant.now());
            given(contacts.findByUserId(payer)).willReturn(Optional.of(disabled));
            assertThatThrownBy(CheckoutServiceTest.this::checkOut).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class Sessions {

        @Test
        void aPaidSubscriptionSaysSoInsteadOfOpeningAnotherCheckout() {
            subscription.setPaidAt(Instant.now());

            assertThat(checkOut().isPaid()).isTrue();
            then(checkout).shouldHaveNoInteractions();
        }

        @Test
        void anOpenSessionIsReusedUnlessItIsAboutToExpire() {
            subscription.setStripeCheckoutSessionId("cs_old");
            subscription.setStripeCheckoutUrl("https://checkout/old");
            subscription.setStripeCheckoutExpiresAt(Instant.now().plus(Duration.ofHours(1)));
            assertThat(checkOut().checkoutUrl()).isEqualTo("https://checkout/old");

            subscription.setStripeCheckoutExpiresAt(Instant.now().plus(Duration.ofMinutes(2)));
            assertThat(checkOut().checkoutUrl()).isEqualTo("https://checkout/1");
        }

        @Test
        void theSessionNeverOutlivesStripesLimitsOrTheSubscriptionsOwnEnd() {
            subscription.setPendingExpiresAt(Instant.now().plus(Duration.ofHours(48)));
            checkOut();
            subscription.setStripeCheckoutSessionId(null);
            subscription.setPendingExpiresAt(Instant.now().plus(Duration.ofMinutes(5)));
            checkOut();
            subscription.setStripeCheckoutSessionId(null);
            subscription.setPendingExpiresAt(Instant.now().plus(Duration.ofHours(3)));
            checkOut();

            ArgumentCaptor<StripeCheckoutSpec> specs = ArgumentCaptor.forClass(StripeCheckoutSpec.class);
            then(checkout).should(org.mockito.Mockito.times(3)).createSubscriptionSession(specs.capture());
            Instant now = Instant.now();
            assertThat(specs.getAllValues().get(0).expiresAt()).isBefore(now.plus(Duration.ofHours(24)));
            assertThat(specs.getAllValues().get(1).expiresAt()).isAfter(now.plus(Duration.ofMinutes(30)));
            assertThat(specs.getAllValues().get(2).expiresAt()).isEqualTo(subscription.getPendingExpiresAt());
            assertThat(specs.getAllValues().get(0).successUrl())
                    .isEqualTo("https://app/ok/" + subscription.getLicenseSubscriptionId());
        }

        @Test
        void aPromotionBecomesACouponForeverOrForItsPeriodsAndNoDiscountNone() {
            subscription.setFirstPriceMinor(700L);
            checkOut();
            then(checkout).should().ensureCoupon(eq("subscription_" + subscription.getLicenseSubscriptionId()),
                    eq(300L), eq("EUR"), eq(null), anyString(), any());

            subscription.setStripeCheckoutSessionId(null);
            subscription.setPromotionPeriods(2);
            checkOut();
            then(checkout).should().ensureCoupon(anyString(), eq(300L), eq("EUR"), eq(2L), anyString(), any());
        }
    }

    @Nested
    class Switching {

        private final BillingSubscription current = PaymentFixtures.active(payer, "sub_old");

        @BeforeEach
        void anotherPaidSubscriptionIsRunning() {
            given(subscriptions.findBySubscriberTypeAndSubscriberIdAndStatusAndStripeSubscriptionIdIsNotNull(
                    LicenseTarget.USER, payer, BillingSubscriptionStatus.ACTIVE)).willReturn(List.of(current));
            given(subscriptions.lockByLicenseSubscriptionId(current.getLicenseSubscriptionId()))
                    .willReturn(Optional.of(current));
        }

        @Test
        void movesTheRunningStripeSubscriptionToTheNewPriceAndActivatesWithoutCheckout() {
            StripeSubscriptionPeriod period = new StripeSubscriptionPeriod("sub_old", "active", Instant.now(),
                    Instant.now().plus(Duration.ofDays(30)), false, Map.of());
            given(stripeSubscriptions.switchPrice(eq("sub_old"), eq("price_1"), any(), any())).willReturn(period);
            // An earlier session, about to expire, is replaced by the switch and closed.
            subscription.setStripeCheckoutSessionId("cs_open");
            subscription.setStripeCheckoutExpiresAt(Instant.now().plus(Duration.ofMinutes(2)));

            assertThat(checkOut().isPaid()).isTrue();

            assertThat(current.getStripeSubscriptionId()).isNull();
            assertThat(subscription.getStripeSubscriptionId()).isEqualTo("sub_old");
            assertThat(subscription.getStripeCheckoutSessionId()).isNull();
            then(checkout).should().expireIfOpen("cs_open");
            then(publisher).should().subscriptionActivated(subscription.getLicenseSubscriptionId(), period);
            then(checkout).should(never()).createSubscriptionSession(any());
        }

        @Test
        void aDeclinedSwitchFallsBackToACheckoutPage() {
            given(stripeSubscriptions.switchPrice(any(), any(), any(), any())).willThrow(new StripeIntegrationException(
                    "declined", new CardException("declined", "req", "card_declined", null, null, null, 402, null)));

            assertThat(checkOut().checkoutUrl()).isEqualTo("https://checkout/1");
            assertThat(current.getStripeSubscriptionId()).isEqualTo("sub_old");
        }

        @Test
        void anyOtherSwitchFailureIsAnError() {
            given(stripeSubscriptions.switchPrice(any(), any(), any(), any())).willThrow(new StripeIntegrationException(
                    "down", new com.stripe.exception.ApiConnectionException("down")));

            assertThatThrownBy(CheckoutServiceTest.this::checkOut).isInstanceOf(StripeIntegrationException.class);
        }
    }
}
