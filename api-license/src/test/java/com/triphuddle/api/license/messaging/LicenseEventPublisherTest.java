package com.triphuddle.api.license.messaging;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.license.LicenseCatalogChangedContract;
import com.triphuddle.amqp.license.LicenseCatalogChangedEvent;
import com.triphuddle.amqp.license.LicenseEntitlementsChangedContract;
import com.triphuddle.amqp.license.LicenseEntitlementsChangedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionActivatedContract;
import com.triphuddle.amqp.license.LicenseSubscriptionActivatedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionActivationRefusedContract;
import com.triphuddle.amqp.license.LicenseSubscriptionActivationRefusedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionCanceledContract;
import com.triphuddle.amqp.license.LicenseSubscriptionCanceledEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionEndedContract;
import com.triphuddle.amqp.license.LicenseSubscriptionEndedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionPendingContract;
import com.triphuddle.amqp.license.LicenseSubscriptionPendingEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionRenewalDueContract;
import com.triphuddle.amqp.license.LicenseSubscriptionRenewalDueEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionRenewedContract;
import com.triphuddle.amqp.license.LicenseSubscriptionRenewedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionResumedContract;
import com.triphuddle.amqp.license.LicenseSubscriptionResumedEvent;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.license.config.LicenseProperties;
import com.triphuddle.api.license.entity.License;
import com.triphuddle.api.license.entity.LicenseStatus;
import com.triphuddle.api.license.entity.Subscription;
import com.triphuddle.api.license.entity.SubscriptionEndReason;
import com.triphuddle.api.license.entity.SubscriptionStatus;
import com.triphuddle.api.license.fixtures.LicenseFixtures;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

/**
 * LIC-8: every change goes out, after commit, to the queue or the fanout exchange its consumers read, with the user
 * its emails go to.
 */
class LicenseEventPublisherTest {

    private final RabbitMQMessageProducer producer = mock(RabbitMQMessageProducer.class);
    private final LicenseEventPublisher publisher = new LicenseEventPublisher(producer,
            new LicenseProperties("EUR", 48, 1, 1, 3, 1));
    private final License userLicense = LicenseFixtures.license(LicenseTarget.USER, LicenseStatus.PUBLISHED, 999);
    private final License groupLicense = LicenseFixtures.license(LicenseTarget.GROUP, LicenseStatus.PUBLISHED, 2999);

    @Test
    void anActivationGoesToItsFanoutWithTheEntitlementsAndTheUserItself() {
        Subscription subscription = LicenseFixtures.subscription(userLicense, SubscriptionStatus.ACTIVE);

        publisher.activated(subscription);

        LicenseSubscriptionActivatedEvent event = sentTo(LicenseSubscriptionActivatedEvent.class,
                LicenseSubscriptionActivatedContract.LICENSE_SUBSCRIPTION_ACTIVATED_EXCHANGE_NAME, "");
        assertThat(event.notifyUserId()).isEqualTo(subscription.getSubscriberId());
        assertThat(event.entitlements()).containsExactly(Map.entry("DESTINATION_ACCESS", "true"));
        assertThat(event.currency()).isEqualTo("EUR");
    }

    @Test
    void aGroupsEmailsGoToWhoeverBoughtItAndAGrantsToNobody() {
        Subscription bought = LicenseFixtures.subscription(groupLicense, SubscriptionStatus.ACTIVE);
        bought.setPurchasedByUserId(UUID.randomUUID());
        Subscription granted = LicenseFixtures.subscription(groupLicense, SubscriptionStatus.ACTIVE);
        granted.setGranted(true);

        publisher.canceled(bought, null);
        assertThat(sentTo(LicenseSubscriptionCanceledEvent.class,
                LicenseSubscriptionCanceledContract.LICENSE_SUBSCRIPTION_CANCELED_EXCHANGE_NAME, "").notifyUserId())
                .isEqualTo(bought.getPurchasedByUserId());

        publisher.ended(granted);
        assertThat(sentTo(LicenseSubscriptionEndedEvent.class,
                LicenseSubscriptionEndedContract.LICENSE_SUBSCRIPTION_ENDED_EXCHANGE_NAME, "").notifyUserId())
                .isNull();
    }

    @Test
    void aPendingSubscriptionCarriesWhatCheckoutNeedsAndWhenItExpires() {
        Subscription pending = LicenseFixtures.subscription(userLicense, SubscriptionStatus.PENDING_PAYMENT);
        pending.setPriceMinor(799);
        pending.setPromotionPeriodsRemaining(3);
        Instant createdAt = Instant.parse("2026-11-16T10:00:00Z");

        publisher.pending(pending, createdAt);

        LicenseSubscriptionPendingEvent event = sentTo(LicenseSubscriptionPendingEvent.class, "",
                LicenseSubscriptionPendingContract.LICENSE_SUBSCRIPTION_PENDING_QUEUE_NAME);
        assertThat(event.listPriceMinor()).isEqualTo(999);
        assertThat(event.priceMinor()).isEqualTo(799);
        assertThat(event.promotionPeriods()).isEqualTo(3);
        assertThat(event.expiresAt()).isEqualTo(createdAt.plus(Duration.ofHours(48)));
    }

    @Test
    void theOtherSubscriptionChangesGoToTheirQueues() {
        Subscription subscription = LicenseFixtures.subscription(userLicense, SubscriptionStatus.ACTIVE);
        subscription.setEndReason(SubscriptionEndReason.SWITCHED);
        UUID actor = UUID.randomUUID();

        publisher.renewed(subscription);
        publisher.resumed(subscription, actor);
        publisher.renewalDue(subscription, 999);
        publisher.activationRefused(subscription, "sub_123");

        assertThat(sentTo(LicenseSubscriptionRenewedEvent.class, "",
                LicenseSubscriptionRenewedContract.LICENSE_SUBSCRIPTION_RENEWED_QUEUE_NAME).currentPeriodEnd())
                .isEqualTo(subscription.getCurrentPeriodEnd());
        assertThat(sentTo(LicenseSubscriptionResumedEvent.class, "",
                LicenseSubscriptionResumedContract.LICENSE_SUBSCRIPTION_RESUMED_QUEUE_NAME).actorId()).isEqualTo(actor);
        assertThat(sentTo(LicenseSubscriptionRenewalDueEvent.class, "",
                LicenseSubscriptionRenewalDueContract.LICENSE_SUBSCRIPTION_RENEWAL_DUE_QUEUE_NAME).priceMinor())
                .isEqualTo(999);
        assertThat(sentTo(LicenseSubscriptionActivationRefusedEvent.class, "",
                LicenseSubscriptionActivationRefusedContract.LICENSE_SUBSCRIPTION_ACTIVATION_REFUSED_QUEUE_NAME)
                .status()).isEqualTo("ACTIVE");
    }

    @Test
    void catalogAndEntitlementChangesCarryTheLicenseAsItIsNow() {
        publisher.catalogChanged(userLicense);
        publisher.entitlementsChanged(userLicense);

        LicenseCatalogChangedEvent catalog = sentTo(LicenseCatalogChangedEvent.class, "",
                LicenseCatalogChangedContract.LICENSE_CATALOG_CHANGED_QUEUE_NAME);
        assertThat(catalog.status()).isEqualTo("PUBLISHED");
        assertThat(catalog.priceMinor()).isEqualTo(999);
        assertThat(sentTo(LicenseEntitlementsChangedEvent.class, "",
                LicenseEntitlementsChangedContract.LICENSE_ENTITLEMENTS_CHANGED_QUEUE_NAME).entitlements())
                .containsEntry("DESTINATION_ACCESS", "true");
    }

    private <T> T sentTo(Class<T> type, String exchange, String queue) {
        ArgumentCaptor<T> event = ArgumentCaptor.forClass(type);
        then(producer).should().publishAfterCommit(event.capture(), eq(exchange), eq(queue));
        return event.getValue();
    }
}
