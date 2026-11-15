package com.triphuddle.api.payment.service;

import com.triphuddle.amqp.license.LicenseSubscriptionActivatedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionEndedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionPendingEvent;
import com.triphuddle.api.payment.entity.BillingSubscription;
import com.triphuddle.api.payment.entity.BillingSubscriptionStatus;
import com.triphuddle.api.payment.messaging.PaymentEventPublisher;
import com.triphuddle.api.payment.repository.BillingSubscriptionRepository;
import com.triphuddle.stripe.checkout.StripeCheckoutClient;
import com.triphuddle.stripe.subscription.StripeSubscriptionClient;
import com.triphuddle.stripe.subscription.StripeSubscriptionPeriod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Follows api-license's subscriptions that wait for payment, so checkout knows what to charge and when to stop,
 * and turns paid checkouts into activations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BillingSubscriptionService {

    private final BillingSubscriptionRepository billingSubscriptionRepository;
    private final StripeCheckoutClient stripeCheckoutClient;
    private final StripeSubscriptionClient stripeSubscriptionClient;
    private final PaymentEventPublisher eventPublisher;

    /**
     * Record a subscription that waits for payment.
     *
     * @param event the pending subscription
     */
    public void onPending(LicenseSubscriptionPendingEvent event) {
        BillingSubscription subscription = findOrNew(event.subscriptionId());
        subscription.setLicenseId(event.licenseId());
        subscription.setSubscriberType(event.subscriberType());
        subscription.setSubscriberId(event.subscriberId());
        subscription.setPurchasedByUserId(event.purchasedByUserId());
        subscription.setBillingInterval(event.billingInterval());
        subscription.setListPriceMinor(event.listPriceMinor());
        subscription.setFirstPriceMinor(event.priceMinor());
        subscription.setCurrency(event.currency());
        subscription.setPromotionPeriods(event.promotionPeriods());
        subscription.setPendingCreatedAt(event.createdAt());
        subscription.setPendingExpiresAt(event.expiresAt());
        // An activation or end that overtook this event keeps its status.
        if (subscription.getStatus() == null) {
            subscription.setStatus(BillingSubscriptionStatus.PENDING);
        }
        billingSubscriptionRepository.save(subscription);
        log.info("Billing subscription recorded [licenseSubscriptionId={}, status={}]", event.subscriptionId(),
                subscription.getStatus());
    }

    /**
     * Follow a subscription that api-license activated, by payment or by staff; grants are not tracked here.
     *
     * @param event the activation
     * @param receivedAt when it arrived, standing in for the activation time
     */
    public void onActivated(LicenseSubscriptionActivatedEvent event, Instant receivedAt) {
        if (event.isGranted()) {
            return;
        }
        // Recorded even if the pending event has not arrived yet, so it can't put it back to PENDING.
        BillingSubscription subscription = findOrNew(event.subscriptionId());
        if (subscription.getStatus() == BillingSubscriptionStatus.ACTIVE
                || subscription.getStatus() == BillingSubscriptionStatus.ENDED) {
            return;
        }
        subscription.setStatus(BillingSubscriptionStatus.ACTIVE);
        subscription.setActivatedAt(receivedAt);
        // Activated by staff while a checkout was open: nobody may pay for it now.
        if (subscription.getPaidAt() == null) {
            expireCheckout(subscription);
        }
        billingSubscriptionRepository.save(subscription);
        log.info("Billing subscription activated [licenseSubscriptionId={}]", event.subscriptionId());
    }

    /**
     * Follow a subscription that is over; one never seen pending is recorded too, in case its pending event
     * comes later, but a grant's end is not.
     *
     * @param event the end
     */
    public void onEnded(LicenseSubscriptionEndedEvent event) {
        boolean isKnown = billingSubscriptionRepository.findByLicenseSubscriptionId(event.subscriptionId())
                .isPresent();
        // A pending subscription that never got here can only have ended as canceled or timed out.
        boolean isEndOfPending = "PENDING_CANCELED".equals(event.endReason())
                || "PENDING_TIMEOUT".equals(event.endReason());
        if (!isKnown && !isEndOfPending) {
            return;
        }
        BillingSubscription subscription = findOrNew(event.subscriptionId());
        expireCheckout(subscription);
        // A Stripe subscription still held here bills nothing more: one switched away from through a new checkout
        // stops at once, without a credit; one Stripe ended is already canceled. A prorated switch moved its
        // Stripe subscription to the new billing subscription, so none is held here.
        if (subscription.getStripeSubscriptionId() != null && subscription.getRefundDueAt() == null) {
            stripeSubscriptionClient.cancelNow(subscription.getStripeSubscriptionId());
        }
        subscription.setStatus(BillingSubscriptionStatus.ENDED);
        subscription.setEndedAt(event.endedAt());
        subscription.setLicenseEndStatus(event.status());
        subscription.setEndReason(event.endReason());
        billingSubscriptionRepository.save(subscription);
        log.info("Billing subscription ended [licenseSubscriptionId={}, endReason={}]", event.subscriptionId(),
                event.endReason());
    }

    /**
     * Follow a paid checkout: record its Stripe subscription and ask api-license to activate it, or stop the
     * Stripe subscription if this one can no longer take the payment.
     *
     * @param licenseSubscriptionId the ID of the subscription in api-license
     * @param sessionId the Stripe checkout session
     * @param customerId the Stripe customer who paid
     * @param stripeSubscriptionId the Stripe subscription it created
     */
    public void onCheckoutPaid(UUID licenseSubscriptionId, String sessionId, String customerId,
                               String stripeSubscriptionId) {
        BillingSubscription subscription = billingSubscriptionRepository
                .lockByLicenseSubscriptionId(licenseSubscriptionId)
                // Thrown so the Stripe event is retried once api-license's pending event has arrived.
                .orElseThrow(() -> new IllegalStateException(
                        "Billing subscription for id=%s not recorded yet".formatted(licenseSubscriptionId)));
        if (stripeSubscriptionId.equals(subscription.getStripeSubscriptionId())) {
            log.info("Checkout payment already handled [licenseSubscriptionId={}]", licenseSubscriptionId);
            return;
        }
        Instant now = Instant.now();
        subscription.setStripeCustomerId(customerId);
        subscription.setStripeSubscriptionId(stripeSubscriptionId);
        subscription.setPaidAt(now);
        clearCheckout(subscription);
        if (subscription.getStatus() != BillingSubscriptionStatus.PENDING) {
            refuse(subscription, now, subscription.getStatus().name());
            return;
        }
        StripeSubscriptionPeriod period = stripeSubscriptionClient.retrieve(stripeSubscriptionId);
        eventPublisher.subscriptionActivated(licenseSubscriptionId, period);
        log.info("Checkout paid [licenseSubscriptionId={}, sessionId={}, stripeSubscriptionId={}]",
                licenseSubscriptionId, sessionId, stripeSubscriptionId);
    }

    /**
     * Forget a checkout session that expired unpaid, so the next checkout opens a new one.
     *
     * @param licenseSubscriptionId the ID of the subscription in api-license
     * @param sessionId the Stripe checkout session
     */
    public void onCheckoutExpired(UUID licenseSubscriptionId, String sessionId) {
        billingSubscriptionRepository.lockByLicenseSubscriptionId(licenseSubscriptionId)
                .filter(subscription -> sessionId.equals(subscription.getStripeCheckoutSessionId()))
                .ifPresent(subscription -> {
                    clearCheckout(subscription);
                    log.info("Checkout expired [licenseSubscriptionId={}, sessionId={}]", licenseSubscriptionId,
                            sessionId);
                });
    }

    /**
     * Stop the Stripe subscription of a payment api-license could not take, as the subscription had moved on.
     *
     * @param licenseSubscriptionId the ID of the subscription in api-license
     * @param stripeSubscriptionId the Stripe subscription that was paid
     * @param licenseStatus the subscription's status in api-license
     */
    public void onActivationRefused(UUID licenseSubscriptionId, String stripeSubscriptionId, String licenseStatus) {
        billingSubscriptionRepository.lockByLicenseSubscriptionId(licenseSubscriptionId)
                .filter(subscription -> stripeSubscriptionId.equals(subscription.getStripeSubscriptionId()))
                .ifPresent(subscription -> refuse(subscription, Instant.now(), licenseStatus));
    }

    // Money came in for a subscription that can't take it: stop Stripe charging again, and flag the payment
    // for staff to refund.
    private void refuse(BillingSubscription subscription, Instant now, String status) {
        stripeSubscriptionClient.cancelNow(subscription.getStripeSubscriptionId());
        if (subscription.getRefundDueAt() == null) {
            subscription.setRefundDueAt(now);
        }
        log.error("Payment for a subscription that no longer waits for it; refund due [licenseSubscriptionId={}, "
                + "status={}, stripeSubscriptionId={}]", subscription.getLicenseSubscriptionId(), status,
                subscription.getStripeSubscriptionId());
    }

    private void expireCheckout(BillingSubscription subscription) {
        if (subscription.getStripeCheckoutSessionId() != null) {
            stripeCheckoutClient.expireIfOpen(subscription.getStripeCheckoutSessionId());
            clearCheckout(subscription);
        }
    }

    private static void clearCheckout(BillingSubscription subscription) {
        subscription.setStripeCheckoutSessionId(null);
        subscription.setStripeCheckoutUrl(null);
        subscription.setStripeCheckoutExpiresAt(null);
    }

    // Locked, as Stripe events about the same subscription may be handled at the same time.
    private BillingSubscription findOrNew(UUID licenseSubscriptionId) {
        return billingSubscriptionRepository.lockByLicenseSubscriptionId(licenseSubscriptionId).orElseGet(() -> {
            BillingSubscription subscription = new BillingSubscription();
            subscription.setLicenseSubscriptionId(licenseSubscriptionId);
            return subscription;
        });
    }
}
