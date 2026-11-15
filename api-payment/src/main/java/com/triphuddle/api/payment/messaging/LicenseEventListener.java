package com.triphuddle.api.payment.messaging;

import com.triphuddle.amqp.license.LicenseCatalogChangedContract;
import com.triphuddle.amqp.license.LicenseCatalogChangedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionActivationRefusedContract;
import com.triphuddle.amqp.license.LicenseSubscriptionActivationRefusedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionCanceledContract;
import com.triphuddle.amqp.license.LicenseSubscriptionCanceledEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionResumedContract;
import com.triphuddle.amqp.license.LicenseSubscriptionResumedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionActivatedContract;
import com.triphuddle.amqp.license.LicenseSubscriptionActivatedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionEndedContract;
import com.triphuddle.amqp.license.LicenseSubscriptionEndedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionPendingContract;
import com.triphuddle.amqp.license.LicenseSubscriptionPendingEvent;
import com.triphuddle.api.payment.service.BillingSubscriptionService;
import com.triphuddle.api.payment.service.CatalogService;
import com.triphuddle.api.payment.service.StripeBillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Follows api-license: its published licenses for the Stripe catalog, and the subscriptions that wait for payment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LicenseEventListener {

    private final CatalogService catalogService;
    private final BillingSubscriptionService billingSubscriptionService;
    private final StripeBillingService stripeBillingService;

    /**
     * Record a license's new state, for the Stripe catalog.
     *
     * @param event the license
     */
    @RabbitListener(queues = LicenseCatalogChangedContract.LICENSE_CATALOG_CHANGED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onCatalogChanged(LicenseCatalogChangedEvent event) {
        log.info("Received license catalog changed [licenseId={}, status={}]", event.licenseId(), event.status());
        catalogService.onCatalogChanged(event);
    }

    /**
     * Record a subscription that waits for payment.
     *
     * @param event the pending subscription
     */
    @RabbitListener(queues = LicenseSubscriptionPendingContract.LICENSE_SUBSCRIPTION_PENDING_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onSubscriptionPending(LicenseSubscriptionPendingEvent event) {
        log.info("Received license subscription pending [subscriptionId={}]", event.subscriptionId());
        billingSubscriptionService.onPending(event);
    }

    /**
     * Follow an activation.
     *
     * @param event the activated subscription
     */
    @RabbitListener(queues = LicenseSubscriptionActivatedContract.LICENSE_SUBSCRIPTION_ACTIVATED_PAYMENT_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onSubscriptionActivated(LicenseSubscriptionActivatedEvent event) {
        log.info("Received license subscription activated [subscriptionId={}]", event.subscriptionId());
        billingSubscriptionService.onActivated(event, Instant.now());
    }

    /**
     * Follow an end.
     *
     * @param event the ended subscription
     */
    @RabbitListener(queues = LicenseSubscriptionEndedContract.LICENSE_SUBSCRIPTION_ENDED_PAYMENT_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onSubscriptionEnded(LicenseSubscriptionEndedEvent event) {
        log.info("Received license subscription ended [subscriptionId={}, endReason={}]", event.subscriptionId(),
                event.endReason());
        billingSubscriptionService.onEnded(event);
    }

    /**
     * Stop the Stripe subscription of a payment api-license could not take.
     *
     * @param event the refusal
     */
    @RabbitListener(
            queues = LicenseSubscriptionActivationRefusedContract.LICENSE_SUBSCRIPTION_ACTIVATION_REFUSED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onActivationRefused(LicenseSubscriptionActivationRefusedEvent event) {
        log.info("Received license subscription activation refused [subscriptionId={}, status={}]",
                event.subscriptionId(), event.status());
        billingSubscriptionService.onActivationRefused(event.subscriptionId(), event.stripeSubscriptionId(),
                event.status());
    }

    /**
     * Stop a paid subscription at the end of its period in Stripe too.
     *
     * @param event the cancel
     */
    @RabbitListener(queues = LicenseSubscriptionCanceledContract.LICENSE_SUBSCRIPTION_CANCELED_PAYMENT_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onSubscriptionCanceled(LicenseSubscriptionCanceledEvent event) {
        log.info("Received license subscription canceled [subscriptionId={}]", event.subscriptionId());
        stripeBillingService.onRenewalRequested(event.subscriptionId(), false);
    }

    /**
     * Keep a paid subscription renewing in Stripe too.
     *
     * @param event the resume
     */
    @RabbitListener(queues = LicenseSubscriptionResumedContract.LICENSE_SUBSCRIPTION_RESUMED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onSubscriptionResumed(LicenseSubscriptionResumedEvent event) {
        log.info("Received license subscription resumed [subscriptionId={}]", event.subscriptionId());
        stripeBillingService.onRenewalRequested(event.subscriptionId(), true);
    }
}
