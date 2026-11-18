package com.triphuddle.api.license.api;

import com.triphuddle.amqp.license.LicenseSubscriptionActivationRefusedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionEndedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionRenewalDueEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionRenewedEvent;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.amqp.payment.PaymentSubscriptionActivatedEvent;
import com.triphuddle.amqp.payment.PaymentSubscriptionEndedEvent;
import com.triphuddle.amqp.payment.PaymentSubscriptionPastDueEvent;
import com.triphuddle.amqp.payment.PaymentSubscriptionRenewalChangedEvent;
import com.triphuddle.amqp.payment.PaymentSubscriptionRenewedEvent;
import com.triphuddle.api.license.entity.DiscountType;
import com.triphuddle.api.license.fixtures.LicenseFixtures;
import com.triphuddle.api.license.messaging.PaymentEventListener;
import com.triphuddle.api.license.service.SubscriptionService;
import com.triphuddle.testsupport.messaging.RecordingMessageProducer.Published;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LIC-6: Stripe-billed subscriptions follow payment events; the others follow the jobs.
 */
class PaymentsAndJobsIT extends LicenseApiSupport {

    @Autowired
    PaymentEventListener payments;

    @Autowired
    SubscriptionService subscriptions;

    private final UUID user = UUID.randomUUID();
    private final Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private final Instant end = start.plus(Duration.ofDays(30));

    @Test
    void aPaymentStartsTheSubscriptionOnStripesPeriodAndARepeatChangesNothing() throws Exception {
        UUID subscription = subscribed(user, publishedLicense(LicenseTarget.USER, 999));

        payments.onSubscriptionActivated(new PaymentSubscriptionActivatedEvent(subscription, "sub_1", start, end));
        payments.onSubscriptionActivated(new PaymentSubscriptionActivatedEvent(subscription, "sub_1", start, end));

        assertThat(statusOf(subscription)).isEqualTo("ACTIVE");
        assertThat(column(subscription, "is_billed_by_stripe", Boolean.class)).isTrue();
        assertThat(column(subscription, "current_period_end", Timestamp.class).toInstant()).isEqualTo(end);
    }

    @Test
    void aPaymentForASubscriptionThatGaveUpWaitingIsRefusedSoItCanBeRefunded() throws Exception {
        UUID subscription = subscribed(user, publishedLicense(LicenseTarget.USER, 999));
        mvc.perform(post(SUBSCRIPTIONS + "/" + subscription + "/cancel").with(Caller.CUSTOMER.request(user)))
                .andExpect(status().isOk());
        published.reset();

        payments.onSubscriptionActivated(new PaymentSubscriptionActivatedEvent(subscription, "sub_2", start, end));

        assertThat(statusOf(subscription)).isEqualTo("ENDED");
        assertThat(published.published()).extracting(Published::payload).singleElement()
                .isEqualTo(new LicenseSubscriptionActivationRefusedEvent(subscription, "sub_2", "ENDED"));
    }

    @Test
    void aPaidRenewalMovesThePeriodOnceAndClearsPastDue() throws Exception {
        UUID subscription = stripeBilled();
        Instant nextEnd = end.plus(Duration.ofDays(30));

        payments.onSubscriptionPastDue(new PaymentSubscriptionPastDueEvent(subscription, end));
        assertThat(column(subscription, "past_due_at", Timestamp.class)).isNotNull();
        payments.onSubscriptionRenewed(new PaymentSubscriptionRenewedEvent(subscription, "sub_3", end, nextEnd));
        payments.onSubscriptionRenewed(new PaymentSubscriptionRenewedEvent(subscription, "sub_3", end, nextEnd));

        assertThat(column(subscription, "renewal_count", Integer.class)).isEqualTo(1);
        assertThat(column(subscription, "past_due_at", Timestamp.class)).isNull();
        assertThat(published.published()).extracting(Published::payload)
                .filteredOn(LicenseSubscriptionRenewedEvent.class::isInstance).hasSize(1);
    }

    @Test
    void stripeEndingItExpiresItAndStripesRenewalSwitchIsMirrored() throws Exception {
        UUID subscription = stripeBilled();

        payments.onRenewalChanged(new PaymentSubscriptionRenewalChangedEvent(subscription, false));
        assertThat(column(subscription, "is_auto_renew", Boolean.class)).isFalse();
        payments.onRenewalChanged(new PaymentSubscriptionRenewalChangedEvent(subscription, true));
        assertThat(column(subscription, "is_auto_renew", Boolean.class)).isTrue();
        payments.onSubscriptionEnded(new PaymentSubscriptionEndedEvent(subscription, "sub_4", end, "canceled"));

        assertThat(statusOf(subscription)).isEqualTo("EXPIRED");
    }

    @Test
    void theRenewalJobRenewsWhatRenewsCountingDownPromotedPeriodsAndExpiresTheRest() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 1000);
        var promo = LicenseFixtures.coded("TWOMONTHS", DiscountType.AMOUNT, 400);
        promo.setDurationPeriods(2);
        send(post(LICENSES + "/" + license + "/promotions"), promo, staff).andExpect(status().isCreated());
        UUID renewing = idOf(body(subscribe(user, license, null, "TWOMONTHS").andExpect(status().isCreated())));
        mvc.perform(post(SUBSCRIPTIONS + "/" + renewing + "/activate").with(staff)).andExpect(status().isOk());
        renewByItself(renewing);
        UUID ending = activated(UUID.randomUUID(), license);
        mvc.perform(post(SUBSCRIPTIONS + "/" + ending + "/cancel").with(staff)).andExpect(status().isOk());
        published.reset();
        Instant twoMonthsOn = Instant.now().plus(Duration.ofDays(62));

        while (subscriptions.renewOrExpireDue(twoMonthsOn, 50) == 50) {
            // a few earlier tests' subscriptions may be due as well
        }

        assertThat(statusOf(renewing)).isEqualTo("ACTIVE");
        assertThat(column(renewing, "renewal_count", Integer.class)).isEqualTo(2);
        assertThat(column(renewing, "price_minor", Long.class)).as("the promoted periods ran out").isEqualTo(1000);
        assertThat(statusOf(ending)).isEqualTo("EXPIRED");
        assertThat(published.published()).extracting(Published::payload)
                .anySatisfy(p -> assertThat(p).isInstanceOfSatisfying(LicenseSubscriptionEndedEvent.class,
                        e -> assertThat(e.subscriptionId()).isEqualTo(ending)));
    }

    @Test
    void anUnpaidSubscriptionEndsAfterItsTimeAndGivesItsPromotionBack() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 1000);
        var once = LicenseFixtures.coded("LASTONE", DiscountType.AMOUNT, 100);
        once.setMaxRedemptions(1);
        send(post(LICENSES + "/" + license + "/promotions"), once, staff).andExpect(status().isCreated());
        UUID pending = idOf(body(subscribe(user, license, null, "LASTONE").andExpect(status().isCreated())));
        jdbc.update("update license_subscriptions set created_at = now() - interval '49 hours' where id = ?", pending);

        subscriptions.endStalePending(Instant.now().minus(Duration.ofHours(48)), 500);

        assertThat(statusOf(pending)).isEqualTo("ENDED");
        assertThat(column(pending, "end_reason", String.class)).isEqualTo("PENDING_TIMEOUT");
        subscribe(UUID.randomUUID(), license, null, "LASTONE").andExpect(status().isCreated());
    }

    @Test
    void aReminderGoesOncePerPeriodWithTheNextPrice() throws Exception {
        UUID subscription = activated(user, publishedLicense(LicenseTarget.USER, 999));
        renewByItself(subscription);
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofDays(40));

        subscriptions.remindDue(now, until, 500);
        subscriptions.remindDue(now, until, 500);

        assertThat(published.published()).extracting(Published::payload)
                .filteredOn(p -> p instanceof LicenseSubscriptionRenewalDueEvent e
                        && e.subscriptionId().equals(subscription))
                .singleElement().satisfies(p -> assertThat(((LicenseSubscriptionRenewalDueEvent) p).priceMinor())
                        .isEqualTo(999));
    }

    // Staff activation leaves a subscription not renewing; canceling and resuming it makes the job renew it.
    private void renewByItself(UUID subscription) throws Exception {
        mvc.perform(post(SUBSCRIPTIONS + "/" + subscription + "/cancel").with(staff)).andExpect(status().isOk());
        mvc.perform(post(SUBSCRIPTIONS + "/" + subscription + "/resume").with(staff)).andExpect(status().isOk());
        published.reset();
    }

    private UUID stripeBilled() throws Exception {
        UUID subscription = subscribed(user, publishedLicense(LicenseTarget.USER, 999));
        payments.onSubscriptionActivated(new PaymentSubscriptionActivatedEvent(subscription, "sub_x", start, end));
        published.reset();
        return subscription;
    }

    private <T> T column(UUID subscription, String column, Class<T> type) {
        return jdbc.queryForObject("select " + column + " from license_subscriptions where id = ?", type, subscription);
    }
}
