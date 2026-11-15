package com.triphuddle.api.payment.service;

import com.triphuddle.api.payment.entity.BillingSubscription;
import com.triphuddle.api.payment.entity.BillingSubscriptionStatus;
import com.triphuddle.api.payment.messaging.PaymentEventPublisher;
import com.triphuddle.api.payment.repository.BillingSubscriptionRepository;
import com.triphuddle.stripe.checkout.StripeCheckoutClient;
import com.triphuddle.stripe.checkout.StripeCheckoutSessionState;
import com.triphuddle.stripe.subscription.StripeSubscriptionClient;
import com.triphuddle.stripe.subscription.StripeSubscriptionPeriod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Compares billing subscriptions with Stripe and sends on what a lost or failed webhook missed. Every message it
 * sends is one its receiver already takes more than once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private static final Set<String> PAID_SESSION_STATUSES = Set.of("paid", "no_payment_required");
    private static final Set<String> ENDED_STATUSES = Set.of("canceled", "incomplete_expired");
    private static final Set<String> UNPAID_STATUSES = Set.of("past_due", "unpaid");

    private final BillingSubscriptionRepository billingSubscriptionRepository;
    private final BillingSubscriptionService billingSubscriptionService;
    private final StripeSubscriptionClient stripeSubscriptionClient;
    private final StripeCheckoutClient stripeCheckoutClient;
    private final PaymentEventPublisher eventPublisher;

    /**
     * Compare the most overdue billing subscription with Stripe, if any.
     *
     * @param now the current time
     * @param cutoff the latest comparison that counts as overdue
     * @return the ID of the subscription compared, or empty if none was due
     * @throws ReconciliationException if reading Stripe or acting on it failed; the transaction is rolled back
     */
    @Transactional
    public Optional<UUID> reconcileNext(Instant now, Instant cutoff) {
        List<BillingSubscription> due = billingSubscriptionRepository.lockDueForReconciliation(cutoff,
                PageRequest.of(0, 1));
        if (due.isEmpty()) {
            return Optional.empty();
        }
        BillingSubscription subscription = due.get(0);
        try {
            if (subscription.getStatus() == BillingSubscriptionStatus.PENDING) {
                reconcileCheckout(subscription);
            } else {
                reconcilePaid(subscription, now);
            }
        } catch (RuntimeException e) {
            throw new ReconciliationException(subscription.getLicenseSubscriptionId(), e);
        }
        subscription.setReconciledAt(now);
        return Optional.of(subscription.getLicenseSubscriptionId());
    }

    /**
     * Note a failed comparison, so the subscription waits for its next turn instead of blocking the others.
     *
     * @param licenseSubscriptionId the ID of the subscription in api-license
     * @param error the failure
     * @param now the current time
     */
    @Transactional
    public void recordFailure(UUID licenseSubscriptionId, Throwable error, Instant now) {
        billingSubscriptionRepository.lockByLicenseSubscriptionId(licenseSubscriptionId)
                .ifPresent(subscription -> subscription.setReconciledAt(now));
        log.warn("Reconciliation failed [licenseSubscriptionId={}]", licenseSubscriptionId, error);
    }

    // A checkout paid or expired without its event arriving.
    private void reconcileCheckout(BillingSubscription subscription) {
        StripeCheckoutSessionState session = stripeCheckoutClient.retrieve(subscription.getStripeCheckoutSessionId());
        UUID licenseSubscriptionId = subscription.getLicenseSubscriptionId();
        if ("complete".equals(session.status()) && PAID_SESSION_STATUSES.contains(session.paymentStatus())
                && session.subscriptionId() != null) {
            log.warn("Reconciliation found a paid checkout [licenseSubscriptionId={}, sessionId={}]",
                    licenseSubscriptionId, session.id());
            billingSubscriptionService.onCheckoutPaid(licenseSubscriptionId, session.id(), session.customerId(),
                    session.subscriptionId());
        } else if ("expired".equals(session.status())) {
            billingSubscriptionService.onCheckoutExpired(licenseSubscriptionId, session.id());
        }
    }

    // A paid subscription's renewal, failed renewal, end or renewal change that api-license may have missed.
    private void reconcilePaid(BillingSubscription subscription, Instant now) {
        UUID licenseSubscriptionId = subscription.getLicenseSubscriptionId();
        StripeSubscriptionPeriod period = stripeSubscriptionClient.retrieve(subscription.getStripeSubscriptionId());
        if (ENDED_STATUSES.contains(period.status())) {
            log.warn("Reconciliation found an ended Stripe subscription [licenseSubscriptionId={}, status={}]",
                    licenseSubscriptionId, period.status());
            eventPublisher.subscriptionEnded(licenseSubscriptionId, period.id(), now, period.status());
            return;
        }
        if (UNPAID_STATUSES.contains(period.status())) {
            eventPublisher.subscriptionPastDue(licenseSubscriptionId, now);
        } else {
            // api-license moves on only to a later period, and otherwise just clears a past due mark.
            eventPublisher.subscriptionRenewed(licenseSubscriptionId, period);
        }
        if (period.isCancelAtPeriodEnd() != subscription.isCancelAtPeriodEnd()) {
            subscription.setCancelAtPeriodEnd(period.isCancelAtPeriodEnd());
            eventPublisher.renewalChanged(licenseSubscriptionId, !period.isCancelAtPeriodEnd());
        }
    }
}
