package com.triphuddle.api.payment.service;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.payment.entity.BillingSubscription;
import com.triphuddle.api.payment.entity.BillingSubscriptionStatus;
import com.triphuddle.api.payment.messaging.PaymentEventPublisher;
import com.triphuddle.api.payment.repository.BillingSubscriptionRepository;
import com.triphuddle.stripe.subscription.StripeSubscriptionClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs paid subscriptions once Stripe bills them: renewals, failed renewals and ends reported by Stripe, and
 * renewal changes made here or there.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StripeBillingService {

    static final String SUBSCRIPTION_CREATE = "subscription_create";
    static final String SUBSCRIPTION_CYCLE = "subscription_cycle";

    private final BillingSubscriptionRepository billingSubscriptionRepository;
    private final StripeSubscriptionClient stripeSubscriptionClient;
    private final PaymentEventPublisher eventPublisher;
    private final PaymentEmailService paymentEmailService;
    private final InvoiceService invoiceService;

    /**
     * Follow a paid invoice: email the payer, and for a renewal move the subscription on in api-license.
     *
     * @param invoice the invoice
     */
    public void onInvoicePaid(StripeInvoice invoice) {
        Optional<BillingSubscription> found = find(invoice);
        if (found.isEmpty()) {
            return;
        }
        BillingSubscription subscription = found.get();
        invoiceService.record(invoice, subscription);
        if (invoice.amountMinor() > 0) {
            paymentEmailService.paymentSucceeded(subscription, invoice.amountMinor(), invoice.currency(),
                    invoice.hostedInvoiceUrl());
        }
        // The first invoice is the checkout's, and a switch's is handled when the switch is made.
        if (SUBSCRIPTION_CYCLE.equals(invoice.billingReason())) {
            eventPublisher.subscriptionRenewed(subscription.getLicenseSubscriptionId(),
                    stripeSubscriptionClient.retrieve(invoice.stripeSubscriptionId()));
            log.info("Renewal paid [licenseSubscriptionId={}, invoiceId={}]", subscription.getLicenseSubscriptionId(),
                    invoice.id());
        }
    }

    /**
     * Follow a failed renewal invoice: api-license marks the subscription past due, and the payer is emailed.
     * Stripe retries it, and ends the subscription after its last try.
     *
     * @param invoice the invoice
     */
    public void onInvoicePaymentFailed(StripeInvoice invoice) {
        if (!SUBSCRIPTION_CYCLE.equals(invoice.billingReason())) {
            // Checkout and switches show a failed first charge to the customer there and then.
            return;
        }
        Optional<BillingSubscription> found = find(invoice);
        if (found.isEmpty()) {
            return;
        }
        BillingSubscription subscription = found.get();
        invoiceService.record(invoice, subscription);
        eventPublisher.subscriptionPastDue(subscription.getLicenseSubscriptionId(), Instant.now());
        paymentEmailService.paymentFailed(subscription, invoice.amountDueMinor(), invoice.currency(),
                invoice.hostedInvoiceUrl(), invoice.nextPaymentAttemptAt());
        log.info("Renewal payment failed [licenseSubscriptionId={}, invoiceId={}, nextAttemptAt={}]",
                subscription.getLicenseSubscriptionId(), invoice.id(), invoice.nextPaymentAttemptAt());
    }

    /**
     * Record an invoice that was voided or marked uncollectible.
     *
     * @param invoice the invoice
     */
    public void onInvoiceChanged(StripeInvoice invoice) {
        if (SUBSCRIPTION_CREATE.equals(invoice.billingReason())
                && billingSubscriptionRepository.lockByStripeSubscriptionId(invoice.stripeSubscriptionId()).isEmpty()) {
            // A first invoice that never got paid at checkout belongs to no billing subscription.
            return;
        }
        find(invoice).ifPresent(subscription -> invoiceService.record(invoice, subscription));
    }

    /**
     * Follow a Stripe subscription Stripe ended, unless we ended it ourselves or it was switched away.
     *
     * @param stripeSubscriptionId the Stripe subscription
     * @param endedAt when it ended
     * @param reason Stripe's reason, or null
     */
    public void onStripeSubscriptionDeleted(String stripeSubscriptionId, Instant endedAt, String reason) {
        billingSubscriptionRepository.lockByStripeSubscriptionId(stripeSubscriptionId)
                .filter(subscription -> subscription.getStatus() == BillingSubscriptionStatus.ACTIVE
                        && subscription.getRefundDueAt() == null)
                .ifPresent(subscription -> {
                    eventPublisher.subscriptionEnded(subscription.getLicenseSubscriptionId(), stripeSubscriptionId,
                            endedAt, reason);
                    log.info("Stripe ended subscription [licenseSubscriptionId={}, reason={}]",
                            subscription.getLicenseSubscriptionId(), reason);
                });
    }

    /**
     * Follow a renewal change made in Stripe, e.g. from the dashboard; our own changes are already recorded.
     *
     * @param stripeSubscriptionId the Stripe subscription
     * @param isCancelAtPeriodEnd whether it now stops at the end of its period
     */
    public void onStripeSubscriptionUpdated(String stripeSubscriptionId, boolean isCancelAtPeriodEnd) {
        billingSubscriptionRepository.lockByStripeSubscriptionId(stripeSubscriptionId)
                .filter(subscription -> subscription.getStatus() == BillingSubscriptionStatus.ACTIVE
                        && subscription.isCancelAtPeriodEnd() != isCancelAtPeriodEnd)
                .ifPresent(subscription -> {
                    subscription.setCancelAtPeriodEnd(isCancelAtPeriodEnd);
                    eventPublisher.renewalChanged(subscription.getLicenseSubscriptionId(), !isCancelAtPeriodEnd);
                    log.info("Renewal changed in Stripe [licenseSubscriptionId={}, isCancelAtPeriodEnd={}]",
                            subscription.getLicenseSubscriptionId(), isCancelAtPeriodEnd);
                });
    }

    /**
     * Carry a cancel or resume made in api-license over to Stripe.
     *
     * @param licenseSubscriptionId the ID of the subscription in api-license
     * @param isAutoRenew whether it should renew at the end of its period
     */
    public void onRenewalRequested(UUID licenseSubscriptionId, boolean isAutoRenew) {
        billingSubscriptionRepository.lockByLicenseSubscriptionId(licenseSubscriptionId)
                .filter(subscription -> subscription.getStatus() == BillingSubscriptionStatus.ACTIVE
                        && subscription.getStripeSubscriptionId() != null
                        && subscription.isCancelAtPeriodEnd() == isAutoRenew)
                .ifPresent(subscription -> setCancelAtPeriodEnd(subscription, !isAutoRenew));
    }

    /**
     * Stop the paid subscriptions of a user who was disabled or deleted at the end of their period, and tell
     * api-license, which does not follow users.
     *
     * @param userId the ID of the user
     */
    public void onUserRemoved(UUID userId) {
        for (BillingSubscription found : billingSubscriptionRepository
                .findBySubscriberTypeAndSubscriberIdAndStatusAndStripeSubscriptionIdIsNotNull(LicenseTarget.USER,
                        userId, BillingSubscriptionStatus.ACTIVE)) {
            billingSubscriptionRepository.lockByLicenseSubscriptionId(found.getLicenseSubscriptionId())
                    .filter(subscription -> !subscription.isCancelAtPeriodEnd())
                    .ifPresent(subscription -> {
                        setCancelAtPeriodEnd(subscription, true);
                        eventPublisher.renewalChanged(subscription.getLicenseSubscriptionId(), false);
                    });
        }
    }

    private void setCancelAtPeriodEnd(BillingSubscription subscription, boolean isCancelAtPeriodEnd) {
        // Recorded first, so the customer.subscription.updated event this causes is recognised as ours.
        subscription.setCancelAtPeriodEnd(isCancelAtPeriodEnd);
        stripeSubscriptionClient.setCancelAtPeriodEnd(subscription.getStripeSubscriptionId(), isCancelAtPeriodEnd);
        log.info("Renewal changed in Stripe [licenseSubscriptionId={}, isCancelAtPeriodEnd={}]",
                subscription.getLicenseSubscriptionId(), isCancelAtPeriodEnd);
    }

    // By the Stripe subscription that bills it, or for a first invoice that overtook its checkout event, by the
    // subscription's metadata; that one is retried until the checkout event has been handled.
    private Optional<BillingSubscription> find(StripeInvoice invoice) {
        Optional<BillingSubscription> found =
                billingSubscriptionRepository.lockByStripeSubscriptionId(invoice.stripeSubscriptionId());
        if (found.isPresent()) {
            return found;
        }
        if (SUBSCRIPTION_CREATE.equals(invoice.billingReason())) {
            throw new IllegalStateException("Invoice %s arrived before its checkout was handled"
                    .formatted(invoice.id()));
        }
        log.info("Invoice for a Stripe subscription no billing subscription holds skipped [invoiceId={}, "
                + "stripeSubscriptionId={}]", invoice.id(), invoice.stripeSubscriptionId());
        return Optional.empty();
    }
}
