package com.triphuddle.api.license.messaging;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.license.LicenseCatalogChangedContract;
import com.triphuddle.amqp.license.LicenseCatalogChangedEvent;
import com.triphuddle.amqp.license.LicenseEntitlementsChangedContract;
import com.triphuddle.amqp.license.LicenseSubscriptionActivationRefusedContract;
import com.triphuddle.amqp.license.LicenseSubscriptionActivationRefusedEvent;
import com.triphuddle.amqp.license.LicenseEntitlementsChangedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionActivatedContract;
import com.triphuddle.amqp.license.LicenseSubscriptionActivatedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionCanceledContract;
import com.triphuddle.amqp.license.LicenseSubscriptionCanceledEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionEndedContract;
import com.triphuddle.amqp.license.LicenseSubscriptionEndedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionPendingContract;
import com.triphuddle.amqp.license.LicenseSubscriptionPendingEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionRenewalDueContract;
import com.triphuddle.amqp.license.LicenseSubscriptionResumedContract;
import com.triphuddle.amqp.license.LicenseSubscriptionResumedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionRenewalDueEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionRenewedContract;
import com.triphuddle.amqp.license.LicenseSubscriptionRenewedEvent;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.license.config.LicenseProperties;
import com.triphuddle.api.license.entity.License;
import com.triphuddle.api.license.entity.LicenseEntitlement;
import com.triphuddle.api.license.entity.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes the license and subscription events, each once its transaction commits.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LicenseEventPublisher {

    private final RabbitMQMessageProducer messageProducer;
    private final LicenseProperties licenseProperties;

    /**
     * Report a subscription that started.
     *
     * @param subscription the active subscription
     */
    public void activated(Subscription subscription) {
        License license = subscription.getLicense();
        publish(new LicenseSubscriptionActivatedEvent(subscription.getId(), license.getId(), license.getCode(),
                license.getName(), subscription.getSubscriberType(), subscription.getSubscriberId(),
                notifyUserIdOf(subscription), subscription.getCurrentPeriodEnd(), subscription.isAutoRenew(),
                subscription.isGranted(), subscription.getPriceMinor(), licenseProperties.currency(),
                entitlementsOf(license)),
                List.of(LicenseSubscriptionActivatedContract.LICENSE_SUBSCRIPTION_ACTIVATED_QUEUE_NAME,
                        LicenseSubscriptionActivatedContract.LICENSE_SUBSCRIPTION_ACTIVATED_PAYMENT_QUEUE_NAME),
                subscription.getId());
    }

    /**
     * Tell api-payment that a payment came for a subscription that no longer waits for one.
     *
     * @param subscription the subscription
     * @param stripeSubscriptionId the Stripe subscription that was paid
     */
    public void activationRefused(Subscription subscription, String stripeSubscriptionId) {
        publish(new LicenseSubscriptionActivationRefusedEvent(subscription.getId(), stripeSubscriptionId,
                subscription.getStatus().name()),
                LicenseSubscriptionActivationRefusedContract.LICENSE_SUBSCRIPTION_ACTIVATION_REFUSED_QUEUE_NAME,
                subscription.getId());
    }

    /**
     * Report a subscription that waits for payment, with what its checkout must charge.
     *
     * @param subscription the pending subscription
     * @param createdAt when it was created
     */
    public void pending(Subscription subscription, Instant createdAt) {
        Instant expiresAt = createdAt.plus(Duration.ofHours(licenseProperties.pendingExpiryHours()));
        publish(new LicenseSubscriptionPendingEvent(subscription.getId(), subscription.getLicense().getId(),
                subscription.getSubscriberType(), subscription.getSubscriberId(),
                subscription.getPurchasedByUserId(), subscription.getBillingInterval(),
                subscription.getListPriceMinor(), subscription.getPriceMinor(), licenseProperties.currency(),
                subscription.getPromotionPeriodsRemaining(), createdAt, expiresAt),
                List.of(LicenseSubscriptionPendingContract.LICENSE_SUBSCRIPTION_PENDING_QUEUE_NAME),
                subscription.getId());
    }

    /**
     * Report a subscription that moved on to its next period.
     *
     * @param subscription the renewed subscription
     */
    public void renewed(Subscription subscription) {
        publish(new LicenseSubscriptionRenewedEvent(subscription.getId(), subscription.getLicense().getId(),
                subscription.getSubscriberType(), subscription.getSubscriberId(),
                subscription.getCurrentPeriodEnd(), subscription.getPriceMinor()),
                LicenseSubscriptionRenewedContract.LICENSE_SUBSCRIPTION_RENEWED_QUEUE_NAME, subscription.getId());
    }

    /**
     * Report a subscription that stopped renewing.
     *
     * @param subscription the canceled subscription
     * @param actorId who canceled it, or null if its group went away
     */
    public void canceled(Subscription subscription, UUID actorId) {
        publish(new LicenseSubscriptionCanceledEvent(subscription.getId(), subscription.getLicense().getId(),
                subscription.getLicense().getName(), subscription.getSubscriberType(),
                subscription.getSubscriberId(), notifyUserIdOf(subscription), actorId,
                subscription.getCurrentPeriodEnd()),
                List.of(LicenseSubscriptionCanceledContract.LICENSE_SUBSCRIPTION_CANCELED_QUEUE_NAME,
                        LicenseSubscriptionCanceledContract.LICENSE_SUBSCRIPTION_CANCELED_PAYMENT_QUEUE_NAME),
                subscription.getId());
    }

    /**
     * Report a canceled subscription that renews again, for api-payment to tell Stripe.
     *
     * @param subscription the resumed subscription
     * @param actorId who resumed it, or null if it was resumed on the Stripe side
     */
    public void resumed(Subscription subscription, UUID actorId) {
        publish(new LicenseSubscriptionResumedEvent(subscription.getId(), actorId),
                LicenseSubscriptionResumedContract.LICENSE_SUBSCRIPTION_RESUMED_QUEUE_NAME, subscription.getId());
    }

    /**
     * Report a subscription that expired or was ended.
     *
     * @param subscription the subscription that is over
     */
    public void ended(Subscription subscription) {
        publish(new LicenseSubscriptionEndedEvent(subscription.getId(), subscription.getLicense().getId(),
                subscription.getLicense().getName(), subscription.getSubscriberType(),
                subscription.getSubscriberId(), notifyUserIdOf(subscription), subscription.getStatus().name(),
                subscription.getEndReason() == null ? null : subscription.getEndReason().name(),
                subscription.getEndedAt()),
                List.of(LicenseSubscriptionEndedContract.LICENSE_SUBSCRIPTION_ENDED_QUEUE_NAME,
                        LicenseSubscriptionEndedContract.LICENSE_SUBSCRIPTION_ENDED_PAYMENT_QUEUE_NAME),
                subscription.getId());
    }

    /**
     * Report a subscription that renews soon.
     *
     * @param subscription the subscription
     * @param nextPriceMinor what its next period costs
     */
    public void renewalDue(Subscription subscription, long nextPriceMinor) {
        publish(new LicenseSubscriptionRenewalDueEvent(subscription.getId(), subscription.getLicense().getId(),
                subscription.getLicense().getName(), subscription.getSubscriberType(),
                subscription.getSubscriberId(), notifyUserIdOf(subscription), subscription.getCurrentPeriodEnd(),
                nextPriceMinor, licenseProperties.currency()),
                LicenseSubscriptionRenewalDueContract.LICENSE_SUBSCRIPTION_RENEWAL_DUE_QUEUE_NAME,
                subscription.getId());
    }

    /**
     * Report a license whose entitlements were replaced.
     *
     * @param license the license
     */
    public void entitlementsChanged(License license) {
        publish(new LicenseEntitlementsChangedEvent(license.getId(), license.getCode(), entitlementsOf(license)),
                LicenseEntitlementsChangedContract.LICENSE_ENTITLEMENTS_CHANGED_QUEUE_NAME, license.getId());
    }

    /**
     * Report a license that left draft and was published, archived or changed, for api-payment's catalog.
     *
     * @param license the license
     */
    public void catalogChanged(License license) {
        publish(new LicenseCatalogChangedEvent(license.getId(), license.getCode(), license.getName(),
                license.getDescription(), license.getTarget(), license.getStatus().name(),
                license.getPriceMinor(), licenseProperties.currency(), license.getBillingInterval(),
                Instant.now()),
                LicenseCatalogChangedContract.LICENSE_CATALOG_CHANGED_QUEUE_NAME, license.getId());
    }

    // The user a subscription's emails go to: the user itself, or for a group whoever bought it;
    // nobody for a group grant, as the staff member who granted it is not the customer.
    private UUID notifyUserIdOf(Subscription subscription) {
        if (subscription.getSubscriberType() == LicenseTarget.USER) {
            return subscription.getSubscriberId();
        }
        return subscription.isGranted() ? null : subscription.getPurchasedByUserId();
    }

    private Map<String, String> entitlementsOf(License license) {
        Map<String, String> entitlements = new LinkedHashMap<>();
        for (LicenseEntitlement entitlement : license.getEntitlements()) {
            entitlements.put(entitlement.getEntitlementKey(), entitlement.getEntitlementValue());
        }
        return entitlements;
    }

    private void publish(Object event, String queueName, UUID id) {
        publish(event, List.of(queueName), id);
    }

    // Built now, while the entities are loaded, and sent once the transaction commits; a rollback drops it.
    // Sent to one queue per consuming service, as a queue has only one consumer.
    private void publish(Object event, List<String> queueNames, UUID id) {
        Runnable send = () -> queueNames.forEach(queueName -> {
            messageProducer.publish(event, "", queueName);
            log.info("Event published [queue={}, id={}]", queueName, id);
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
