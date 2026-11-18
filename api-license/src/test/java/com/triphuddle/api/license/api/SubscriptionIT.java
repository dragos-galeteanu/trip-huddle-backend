package com.triphuddle.api.license.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.amqp.license.LicenseSubscriptionActivatedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionCanceledEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionEndedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionPendingEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionResumedEvent;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.license.entity.DiscountType;
import com.triphuddle.api.license.fixtures.LicenseFixtures;
import com.triphuddle.testsupport.messaging.RecordingMessageProducer.Published;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LIC-2, LIC-4, LIC-5 and LIC-7: subscribing, switching, groups, grants and extensions.
 */
class SubscriptionIT extends LicenseApiSupport {

    private final UUID user = UUID.randomUUID();

    @Test
    void subscribingWaitsForPaymentAtThePriceQuotedAndTellsPayments() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 999);

        JsonNode pending = body(subscribe(user, license, null, null).andExpect(status().isCreated()));

        assertThat(pending.path("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(pending.path("priceMinor").asLong()).isEqualTo(999);
        assertThat(pending.path("createdAt").isNull()).isFalse();
        assertThat(published.published()).extracting(Published::payload)
                .anySatisfy(p -> assertThat(p).isInstanceOfSatisfying(LicenseSubscriptionPendingEvent.class, e -> {
                    assertThat(e.subscriberId()).isEqualTo(user);
                    assertThat(e.expiresAt()).isAfter(e.createdAt());
                }));
    }

    @Test
    void aUserHasOnePendingSubscriptionAtATimeAndCannotResubscribeToTheSameLicense() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 999);
        UUID first = subscribed(user, license);

        subscribe(user, license, null, null).andExpect(status().isUnprocessableEntity());
        mvc.perform(post(SUBSCRIPTIONS + "/" + first + "/activate").with(staff)).andExpect(status().isOk());
        subscribe(user, license, null, null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Already subscribed to this license"));
    }

    @Test
    void subscribingToAnotherLicenseIsASwitchOnceItStarts() throws Exception {
        UUID old = activated(user, publishedLicense(LicenseTarget.USER, 999));
        UUID next = subscribed(user, publishedLicense(LicenseTarget.USER, 1999));
        assertThat(statusOf(old)).as("still active while the switch waits for payment").isEqualTo("ACTIVE");
        published.reset();

        mvc.perform(post(SUBSCRIPTIONS + "/" + next + "/activate").with(staff)).andExpect(status().isOk());

        assertThat(statusOf(old)).isEqualTo("ENDED");
        assertThat(jdbc.queryForObject("select end_reason from license_subscriptions where id = ?", String.class, old))
                .isEqualTo("SWITCHED");
        assertThat(jdbc.queryForObject("select switched_to_subscription_id from license_subscriptions where id = ?",
                UUID.class, old)).isEqualTo(next);
        assertThat(published.published()).extracting(Published::payload)
                .anySatisfy(p -> assertThat(p).isInstanceOf(LicenseSubscriptionEndedEvent.class))
                .anySatisfy(p -> assertThat(p).isInstanceOf(LicenseSubscriptionActivatedEvent.class));
        mvc.perform(get(SUBSCRIPTIONS + "/mine/active").with(Caller.CUSTOMER.request(user)))
                .andExpect(jsonPath("$.id").value(next.toString()));
    }

    @Test
    void theDatabaseAllowsOneActiveSubscriptionPerSubscriber() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 999);
        activated(user, license);

        assertThatThrownBy(() -> jdbc.update("""
                insert into license_subscriptions (id, license_id, subscriber_type, subscriber_id,
                    purchased_by_user_id, status, billing_interval, list_price_minor, price_minor, is_auto_renew,
                    renewal_count, is_granted, is_billed_by_stripe, created_at, updated_at)
                values (gen_random_uuid(), ?, 'USER', ?, ?, 'ACTIVE', 'MONTH', 999, 999, true, 0, false, false,
                    now(), now())""", license, user, user)).hasMessageContaining("license_subscriptions_active_uq");
    }

    @Test
    void aSubscriberKeepsTheirPriceWhenTheLicenseGetsDearer() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 999);
        UUID subscription = activated(user, license);
        String code = jdbc.queryForObject("select code from licenses where id = ?", String.class, license);

        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(LICENSES + "/" + license),
                Map.of("code", code, "name", "Explorer", "target", "USER", "priceMinor", 1999,
                        "billingInterval", "MONTH"), staff).andExpect(status().isOk());

        mvc.perform(get(SUBSCRIPTIONS + "/" + subscription).with(Caller.CUSTOMER.request(user)))
                .andExpect(jsonPath("$.priceMinor").value(999));
    }

    @Test
    void cancelingStopsTheRenewalAndResumingRestartsIt() throws Exception {
        UUID subscription = activated(user, publishedLicense(LicenseTarget.USER, 999));
        published.reset();

        mvc.perform(post(SUBSCRIPTIONS + "/" + subscription + "/cancel").with(Caller.CUSTOMER.request(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isAutoRenew").value(false))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(post(SUBSCRIPTIONS + "/" + subscription + "/cancel").with(Caller.CUSTOMER.request(user)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post(SUBSCRIPTIONS + "/" + subscription + "/resume").with(Caller.CUSTOMER.request(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isAutoRenew").value(true));
        mvc.perform(post(SUBSCRIPTIONS + "/" + subscription + "/resume").with(Caller.CUSTOMER.request(user)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post(SUBSCRIPTIONS + "/" + subscription + "/cancel").with(Caller.CUSTOMER.request()))
                .andExpect(status().isForbidden());

        assertThat(published.published()).extracting(Published::payload)
                .anySatisfy(p -> assertThat(p).isInstanceOf(LicenseSubscriptionCanceledEvent.class))
                .anySatisfy(p -> assertThat(p).isInstanceOf(LicenseSubscriptionResumedEvent.class));
    }

    @Test
    void cancelingWhilePendingEndsItAndGivesThePromotionBack() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 1000);
        var once = LicenseFixtures.coded("SOLO", DiscountType.AMOUNT, 100);
        once.setMaxRedemptions(1);
        send(post(LICENSES + "/" + license + "/promotions"), once, staff).andExpect(status().isCreated());
        UUID pending = idOf(body(subscribe(user, license, null, "SOLO").andExpect(status().isCreated())));

        mvc.perform(post(SUBSCRIPTIONS + "/" + pending + "/cancel").with(Caller.CUSTOMER.request(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ENDED"))
                .andExpect(jsonPath("$.endReason").value("PENDING_CANCELED"));

        subscribe(UUID.randomUUID(), license, null, "SOLO").andExpect(status().isCreated());
    }

    @Test
    void onlyAGroupsAdminsSubscribeItAndSeeItsSubscriptions() throws Exception {
        UUID license = publishedLicense(LicenseTarget.GROUP, 2999);
        UUID group = UUID.randomUUID();
        makeGroupAdmin(group, user);

        subscribe(UUID.randomUUID(), license, group, null).andExpect(status().isForbidden());
        subscribe(user, license, null, null).andExpect(status().isUnprocessableEntity());
        UUID subscription = idOf(body(subscribe(user, license, group, null).andExpect(status().isCreated())));

        mvc.perform(get(SUBSCRIPTIONS + "/groups/" + group).param("page", "1").param("pageSize", "5")
                .with(Caller.CUSTOMER.request(user))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(subscription.toString()));
        mvc.perform(get(SUBSCRIPTIONS + "/groups/" + group).param("page", "1").param("pageSize", "5")
                .with(Caller.CUSTOMER.request())).andExpect(status().isForbidden());
        subscribe(user, publishedLicense(LicenseTarget.USER, 999), group, null)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aRemovedGroupsSubscriptionsStopAndItsAdminsLoseTheirRights() throws Exception {
        UUID license = publishedLicense(LicenseTarget.GROUP, 2999);
        UUID group = UUID.randomUUID();
        makeGroupAdmin(group, user);
        UUID subscription = idOf(body(subscribe(user, license, group, null).andExpect(status().isCreated())));
        mvc.perform(post(SUBSCRIPTIONS + "/" + subscription + "/activate").with(staff)).andExpect(status().isOk());

        groupEvents.onGroupDeleted(new com.triphuddle.amqp.group.GroupDeletedEvent(group, "Trip"));

        assertThat(jdbc.queryForObject("select is_auto_renew from license_subscriptions where id = ?", Boolean.class,
                subscription)).isFalse();
        subscribe(user, publishedLicense(LicenseTarget.GROUP, 1999), group, null).andExpect(status().isForbidden());
    }

    @Test
    void staffGrantBetweenOneAndAHundredTwentyPeriodsThatNeverRenew() throws Exception {
        UUID license = publishedLicense(LicenseTarget.USER, 999);

        send(post(SUBSCRIPTIONS + "/grant"), Map.of("userId", user, "licenseId", license, "periods", 121), staff)
                .andExpect(status().isBadRequest());
        JsonNode granted = body(send(post(SUBSCRIPTIONS + "/grant"),
                Map.of("userId", user, "licenseId", license, "periods", 3), staff).andExpect(status().isCreated()));

        assertThat(granted.path("isGranted").asBoolean()).isTrue();
        assertThat(granted.path("isAutoRenew").asBoolean()).isFalse();
        assertThat(granted.path("priceMinor").asLong()).isZero();
        mvc.perform(post(SUBSCRIPTIONS + "/" + idOf(granted) + "/cancel").with(staff))
                .andExpect(status().isUnprocessableEntity());
        send(post(SUBSCRIPTIONS + "/grant"), Map.of("licenseId", license, "periods", 1), staff)
                .andExpect(status().isUnprocessableEntity());
        send(post(SUBSCRIPTIONS + "/grant"), Map.of("userId", user, "licenseId", license, "periods", 1),
                Caller.CUSTOMER.request()).andExpect(status().isForbidden());
    }

    @Test
    void staffExtendASubscriptionByOneToThreeThousandSixHundredFiftyDays() throws Exception {
        UUID subscription = activated(user, publishedLicense(LicenseTarget.USER, 999));
        java.time.Instant end = jdbc.queryForObject("select current_period_end from license_subscriptions where id = ?",
                java.sql.Timestamp.class, subscription).toInstant();

        send(post(SUBSCRIPTIONS + "/" + subscription + "/extend"), Map.of("days", 0), staff)
                .andExpect(status().isBadRequest());
        send(post(SUBSCRIPTIONS + "/" + subscription + "/extend"), Map.of("days", 10), staff)
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select current_period_end from license_subscriptions where id = ?",
                java.sql.Timestamp.class, subscription).toInstant())
                .isEqualTo(end.plus(java.time.Duration.ofDays(10)));
        jdbc.update("update license_subscriptions set is_billed_by_stripe = true where id = ?", subscription);
        send(post(SUBSCRIPTIONS + "/" + subscription + "/extend"), Map.of("days", 10), staff)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void customersSeeOnlyTheirOwnSubscriptionsAndStaffSeeAll() throws Exception {
        UUID subscription = subscribed(user, publishedLicense(LicenseTarget.USER, 999));

        mvc.perform(get(SUBSCRIPTIONS + "/mine").param("page", "1").param("pageSize", "5")
                .with(Caller.CUSTOMER.request(user))).andExpect(jsonPath("$.data[0].id").value(subscription.toString()));
        mvc.perform(get(SUBSCRIPTIONS + "/" + subscription).with(Caller.CUSTOMER.request()))
                .andExpect(status().isForbidden());
        mvc.perform(get(SUBSCRIPTIONS).param("page", "1").param("pageSize", "5").param("subscriberId", user.toString())
                .with(Caller.INSPECTOR.request())).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get(SUBSCRIPTIONS + "/mine/active").with(Caller.CUSTOMER.request(user)))
                .andExpect(status().isNoContent());
    }
}
