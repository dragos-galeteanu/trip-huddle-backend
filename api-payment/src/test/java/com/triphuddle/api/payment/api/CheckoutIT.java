package com.triphuddle.api.payment.api;

import com.triphuddle.amqp.payment.PaymentSubscriptionActivatedEvent;
import com.triphuddle.stripe.checkout.StripeCheckoutSession;
import com.triphuddle.stripe.checkout.StripeCheckoutSpec;
import com.triphuddle.testsupport.messaging.RecordingMessageProducer.Published;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PAY-2: checkout opens only for a subscription waiting for payment, and the first payment activates it.
 */
class CheckoutIT extends PaymentApiSupport {

    private void givenSessions() {
        given(stripeCheckout.createSubscriptionSession(any())).willAnswer(i -> new StripeCheckoutSession(
                "cs_" + UUID.randomUUID(), "https://checkout.stripe.com/pay", Instant.now().plus(Duration.ofHours(23))));
    }

    @Test
    void opensACheckoutForTheQuotedPriceWithAPromotionAsAOneOffCoupon() throws Exception {
        givenSessions();
        UUID license = syncedLicense(1000);
        UUID user = payer(true);
        UUID subscription = pending(license, user, 1000, 700, 3);

        send(post(CHECKOUT), Map.of("subscriptionId", subscription), Caller.CUSTOMER.request(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.stripe.com/pay"))
                .andExpect(jsonPath("$.isPaid").value(false));

        then(stripeCustomers).should().create(eq("payer-" + user + "@example.com"), eq("Ana Pop"), any(),
                eq("customer-user-" + user));
        then(stripeCheckout).should().ensureCoupon(eq("subscription_" + subscription), eq(300L), eq("EUR"), eq(3L),
                anyString(), any());
        ArgumentCaptor<StripeCheckoutSpec> spec = ArgumentCaptor.forClass(StripeCheckoutSpec.class);
        then(stripeCheckout).should().createSubscriptionSession(spec.capture());
        assertThat(spec.getValue().couponId()).isEqualTo("subscription_" + subscription);
        assertThat(spec.getValue().clientReferenceId()).isEqualTo(subscription.toString());
        assertThat(spec.getValue().successUrl()).contains(subscription.toString());
    }

    @Test
    void reusesAnOpenSessionAndTheSameCustomer() throws Exception {
        givenSessions();
        UUID user = payer(true);
        UUID subscription = pending(syncedLicense(999), user, 999, 999, null);

        send(post(CHECKOUT), Map.of("subscriptionId", subscription), Caller.CUSTOMER.request(user)).andExpect(status().isOk());
        send(post(CHECKOUT), Map.of("subscriptionId", subscription), Caller.CUSTOMER.request(user)).andExpect(status().isOk());

        then(stripeCheckout).should(times(1)).createSubscriptionSession(any());
        then(stripeCheckout).should(never()).ensureCoupon(any(), org.mockito.ArgumentMatchers.anyLong(), any(), any(),
                any(), any());
        then(stripeCustomers).should(times(1)).create(any(), any(), any(), any());
    }

    @Test
    void thePaymentActivatesTheSubscriptionInApiLicenseForStripesPeriodAndCheckoutThenSaysPaid() throws Exception {
        givenSessions();
        UUID user = payer(true);
        UUID subscription = pending(syncedLicense(999), user, 999, 999, null);
        send(post(CHECKOUT), Map.of("subscriptionId", subscription), Caller.CUSTOMER.request(user)).andExpect(status().isOk());

        String stripeSubscriptionId = paidThroughCheckout(subscription);

        assertThat(published.published()).extracting(Published::payload).contains(
                new PaymentSubscriptionActivatedEvent(subscription, stripeSubscriptionId, periodStart, periodEnd));
        assertThat(billing(subscription, "stripe_subscription_id", String.class)).isEqualTo(stripeSubscriptionId);
        send(post(CHECKOUT), Map.of("subscriptionId", subscription), Caller.CUSTOMER.request(user))
                .andExpect(jsonPath("$.isPaid").value(true));
    }

    @Test
    void refusesWhatIsNotWaitingForPaymentOrIsNotSetUpYet() throws Exception {
        UUID user = payer(true);
        send(post(CHECKOUT), Map.of("subscriptionId", UUID.randomUUID()), Caller.CUSTOMER.request(user))
                .andExpect(status().isNotFound());

        UUID notSynced = pending(UUID.randomUUID(), user, 999, 999, null);
        send(post(CHECKOUT), Map.of("subscriptionId", notSynced), Caller.CUSTOMER.request(user))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("being set up")));

        UUID unknownPayer = UUID.randomUUID();
        UUID noContact = pending(syncedLicense(999), unknownPayer, 999, 999, null);
        send(post(CHECKOUT), Map.of("subscriptionId", noContact), Caller.CUSTOMER.request(unknownPayer))
                .andExpect(status().isUnprocessableEntity());

        UUID expired = pending(syncedLicense(999), user, 999, 999, null);
        jdbc.update("update payment_subscriptions set pending_expires_at = now() - interval '1 minute' "
                + "where license_subscription_id = ?", expired);
        send(post(CHECKOUT), Map.of("subscriptionId", expired), Caller.CUSTOMER.request(user))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void onlyTheSubscriberChecksOut() throws Exception {
        UUID user = payer(true);
        UUID subscription = pending(syncedLicense(999), user, 999, 999, null);

        send(post(CHECKOUT), Map.of("subscriptionId", subscription), Caller.CUSTOMER.request())
                .andExpect(status().isForbidden());
        mvc.perform(post(CHECKOUT).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
    }
}
