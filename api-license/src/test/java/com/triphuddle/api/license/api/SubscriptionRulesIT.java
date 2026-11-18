package com.triphuddle.api.license.api;

import com.triphuddle.amqp.group.GroupDisabledEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionActivationRefusedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionCanceledEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionEndedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionRenewalDueEvent;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LIC-4, LIC-5 and LIC-6: a group's active subscription, removed groups, grants that can't be given, and
 * payments that arrive for subscriptions that moved on.
 */
class SubscriptionRulesIT extends LicenseApiSupport {

    @Autowired
    PaymentEventListener payments;

    @Autowired
    SubscriptionService subscriptions;

    private final UUID admin = UUID.randomUUID();
    private final UUID group = UUID.randomUUID();
    private final Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private final Instant end = start.plus(Duration.ofDays(30));

    @Test
    void aGroupsAdminsAndStaffSeeItsActiveSubscriptionAndNobodyElseDoes() throws Exception {
        makeGroupAdmin(group, admin);
        mvc.perform(get(SUBSCRIPTIONS + "/groups/" + group + "/active").with(Caller.CUSTOMER.request(admin)))
                .andExpect(status().isNoContent());
        UUID subscription = activeGroupSubscription();

        mvc.perform(get(SUBSCRIPTIONS + "/groups/" + group + "/active").with(Caller.CUSTOMER.request(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(subscription.toString()));
        mvc.perform(get(SUBSCRIPTIONS + "/groups/" + group + "/active").with(Caller.INSPECTOR.request()))
                .andExpect(status().isOk());
        mvc.perform(get(SUBSCRIPTIONS + "/groups/" + group + "/active").with(Caller.CUSTOMER.request()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aDisabledGroupsRenewingSubscriptionRunsOutAndItsPendingOneEnds() throws Exception {
        makeGroupAdmin(group, admin);
        UUID active = activeGroupSubscription();
        mvc.perform(post(SUBSCRIPTIONS + "/" + active + "/cancel").with(staff)).andExpect(status().isOk());
        mvc.perform(post(SUBSCRIPTIONS + "/" + active + "/resume").with(staff)).andExpect(status().isOk());
        UUID pending = idOf(body(subscribe(admin, publishedLicense(LicenseTarget.GROUP, 3999), group, null)
                .andExpect(status().isCreated())));
        published.reset();

        groupEvents.onGroupDisabled(new GroupDisabledEvent(group, "Trip"));

        assertThat(statusOf(active)).as("runs out its paid period").isEqualTo("ACTIVE");
        assertThat(column(active, "is_auto_renew", Boolean.class)).isFalse();
        assertThat(column(active, "canceled_at", Timestamp.class)).isNotNull();
        assertThat(statusOf(pending)).isEqualTo("ENDED");
        assertThat(column(pending, "end_reason", String.class)).isEqualTo("PENDING_CANCELED");
        assertThat(published.published()).extracting(Published::payload)
                .anySatisfy(p -> assertThat(p).isInstanceOfSatisfying(LicenseSubscriptionCanceledEvent.class,
                        e -> assertThat(e.subscriptionId()).isEqualTo(active)))
                .anySatisfy(p -> assertThat(p).isInstanceOfSatisfying(LicenseSubscriptionEndedEvent.class,
                        e -> assertThat(e.subscriptionId()).isEqualTo(pending)));

        published.reset();
        groupEvents.onGroupDisabled(new GroupDisabledEvent(group, "Trip"));
        assertThat(published.published()).as("nothing is left to stop").isEmpty();
    }

    @Test
    void onlyAPendingSubscriptionIsActivatedByHand() throws Exception {
        UUID subscription = activated(admin, publishedLicense(LicenseTarget.USER, 999));

        mvc.perform(post(SUBSCRIPTIONS + "/" + subscription + "/activate").with(staff))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("not waiting")));
        mvc.perform(post(SUBSCRIPTIONS + "/" + UUID.randomUUID() + "/activate").with(staff))
                .andExpect(status().isNotFound());
    }

    @Test
    void aGrantNamesExactlyOneSubscriberOfAPublishedOrArchivedLicense() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 999);

        send(post(SUBSCRIPTIONS + "/grant"), Map.of("userId", admin, "groupId", group, "licenseId", license,
                "periods", 1), staff).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Give either a user ID or a group ID"));
        send(post(SUBSCRIPTIONS + "/grant"), Map.of("userId", admin, "licenseId", draft(LicenseTarget.USER, 999),
                "periods", 1), staff).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("is a draft")));
        send(post(SUBSCRIPTIONS + "/grant"), Map.of("userId", admin, "licenseId", UUID.randomUUID(),
                "periods", 1), staff).andExpect(status().isNotFound());
    }

    @Test
    void aRenewalPaidForASubscriptionThatEndedIsRefusedSoItCanBeRefunded() throws Exception {
        UUID subscription = stripeBilled();
        payments.onSubscriptionEnded(new PaymentSubscriptionEndedEvent(subscription, "sub_r", end, "canceled"));
        published.reset();

        payments.onSubscriptionRenewed(new PaymentSubscriptionRenewedEvent(subscription, "sub_r", end,
                end.plus(Duration.ofDays(30))));

        assertThat(statusOf(subscription)).isEqualTo("EXPIRED");
        assertThat(published.published()).extracting(Published::payload).singleElement()
                .isEqualTo(new LicenseSubscriptionActivationRefusedEvent(subscription, "sub_r", "EXPIRED"));
    }

    @Test
    void aRetryThatPaysTheCurrentPeriodClearsPastDueWithoutRenewing() throws Exception {
        UUID subscription = stripeBilled();
        payments.onSubscriptionPastDue(new PaymentSubscriptionPastDueEvent(subscription, end));

        payments.onSubscriptionRenewed(new PaymentSubscriptionRenewedEvent(subscription, "sub_r", start, end));

        assertThat(column(subscription, "past_due_at", Timestamp.class)).isNull();
        assertThat(column(subscription, "renewal_count", Integer.class)).isZero();
        assertThat(column(subscription, "current_period_end", Timestamp.class).toInstant()).isEqualTo(end);
    }

    @Test
    void aPaymentForASubscriptionStaffAlreadyActivatedIsRefused() throws Exception {
        UUID subscription = activated(admin, publishedLicense(LicenseTarget.USER, 999));
        published.reset();

        payments.onSubscriptionActivated(new PaymentSubscriptionActivatedEvent(subscription, "sub_a", start, end));

        assertThat(published.published()).extracting(Published::payload).singleElement()
                .isEqualTo(new LicenseSubscriptionActivationRefusedEvent(subscription, "sub_a", "ACTIVE"));
    }

    @Test
    void anEndedSubscriptionCantBeCanceledResumedOrExtended() throws Exception {
        UUID subscription = subscribed(admin, publishedLicense(LicenseTarget.USER, 999));
        mvc.perform(post(SUBSCRIPTIONS + "/" + subscription + "/cancel").with(staff)).andExpect(status().isOk());

        mvc.perform(post(SUBSCRIPTIONS + "/" + subscription + "/cancel").with(staff))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(endsWith("is over")));
        mvc.perform(post(SUBSCRIPTIONS + "/" + subscription + "/resume").with(staff))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(endsWith("is ENDED, not ACTIVE")));
        send(post(SUBSCRIPTIONS + "/" + subscription + "/extend"), Map.of("days", 5), staff)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void subscribingAgainToACanceledLicenseAsksForAResumeInstead() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 999);
        UUID subscription = activated(admin, license);
        mvc.perform(post(SUBSCRIPTIONS + "/" + subscription + "/cancel").with(staff)).andExpect(status().isOk());

        subscribe(admin, license, null, null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Already subscribed to this license, resume it instead"));
    }

    @Test
    void aStripeRenewalChangeThatChangesNothingOrIsForAnotherSubscriptionIsIgnored() throws Exception {
        UUID subscription = stripeBilled();

        payments.onRenewalChanged(new PaymentSubscriptionRenewalChangedEvent(subscription, true));
        payments.onRenewalChanged(new PaymentSubscriptionRenewalChangedEvent(UUID.randomUUID(), false));

        assertThat(column(subscription, "is_auto_renew", Boolean.class)).isTrue();
        assertThat(published.published()).isEmpty();
    }

    @Test
    void theReminderBeforeTheLastPromotedPeriodRunsOutQuotesTheListPrice() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 1000);
        var promo = LicenseFixtures.coded("ONEMONTH", DiscountType.AMOUNT, 400);
        promo.setDurationPeriods(1);
        send(post(LICENSES + "/" + license + "/promotions"), promo, staff).andExpect(status().isCreated());
        UUID subscription = idOf(body(subscribe(admin, license, null, "ONEMONTH").andExpect(status().isCreated())));
        mvc.perform(post(SUBSCRIPTIONS + "/" + subscription + "/activate").with(staff)).andExpect(status().isOk());
        mvc.perform(post(SUBSCRIPTIONS + "/" + subscription + "/cancel").with(staff)).andExpect(status().isOk());
        mvc.perform(post(SUBSCRIPTIONS + "/" + subscription + "/resume").with(staff)).andExpect(status().isOk());
        published.reset();
        Instant now = Instant.now();

        while (subscriptions.remindDue(now, now.plus(Duration.ofDays(40)), 50) == 50) {
            // earlier tests' subscriptions may be due as well
        }

        assertThat(published.published()).extracting(Published::payload)
                .filteredOn(p -> p instanceof LicenseSubscriptionRenewalDueEvent e
                        && e.subscriptionId().equals(subscription))
                .singleElement().satisfies(p -> assertThat(((LicenseSubscriptionRenewalDueEvent) p).priceMinor())
                        .isEqualTo(1000));
    }

    private UUID activeGroupSubscription() throws Exception {
        UUID subscription = idOf(body(subscribe(admin, publishedLicense(LicenseTarget.GROUP, 2999), group, null)
                .andExpect(status().isCreated())));
        mvc.perform(post(SUBSCRIPTIONS + "/" + subscription + "/activate").with(staff)).andExpect(status().isOk());
        return subscription;
    }

    private UUID stripeBilled() throws Exception {
        UUID subscription = subscribed(admin, publishedLicense(LicenseTarget.USER, 999));
        payments.onSubscriptionActivated(new PaymentSubscriptionActivatedEvent(subscription, "sub_r", start, end));
        published.reset();
        return subscription;
    }

    private <T> T column(UUID subscription, String column, Class<T> type) {
        return jdbc.queryForObject("select " + column + " from license_subscriptions where id = ?", type, subscription);
    }
}
