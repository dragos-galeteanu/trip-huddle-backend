package com.triphuddle.api.payment.service;

import com.triphuddle.api.payment.config.PaymentProperties;
import com.triphuddle.api.payment.dto.CheckoutResponse;
import com.triphuddle.api.payment.entity.BillingSubscription;
import com.triphuddle.api.payment.entity.BillingSubscriptionStatus;
import com.triphuddle.api.payment.entity.LicensePrice;
import com.triphuddle.api.payment.entity.LicenseProduct;
import com.triphuddle.api.payment.entity.UserContact;
import com.triphuddle.api.payment.messaging.PaymentEventPublisher;
import com.triphuddle.api.payment.repository.BillingSubscriptionRepository;
import com.triphuddle.api.payment.repository.LicenseProductRepository;
import com.triphuddle.api.payment.repository.UserContactRepository;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.stripe.checkout.StripeCheckoutClient;
import com.triphuddle.stripe.checkout.StripeCheckoutSession;
import com.triphuddle.stripe.checkout.StripeCheckoutSpec;
import com.triphuddle.stripe.exception.StripeIntegrationException;
import com.triphuddle.stripe.subscription.StripeSubscriptionClient;
import com.triphuddle.stripe.subscription.StripeSubscriptionPeriod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pays for a subscription waiting for payment, at the price api-license quoted for it: by switching the
 * subscriber's paid subscription to it with proration, or else through a Stripe Checkout page.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CheckoutService {

    // Stripe takes sessions that expire between 30 minutes and 24 hours from now.
    private static final Duration MIN_SESSION_LIFETIME = Duration.ofMinutes(30);
    private static final Duration MAX_SESSION_LIFETIME = Duration.ofHours(24);
    // An open session closer than this to its end is replaced, so the customer isn't sent to a dying page.
    private static final Duration REUSE_MARGIN = Duration.ofMinutes(5);
    private static final int PAYMENT_REQUIRED = 402;

    private final BillingSubscriptionRepository billingSubscriptionRepository;
    private final LicenseProductRepository licenseProductRepository;
    private final UserContactRepository userContactRepository;
    private final CatalogSyncService catalogSyncService;
    private final CustomerService customerService;
    private final StripeCheckoutClient stripeCheckoutClient;
    private final StripeSubscriptionClient stripeSubscriptionClient;
    private final PaymentEventPublisher eventPublisher;
    private final PaymentProperties paymentProperties;

    /**
     * Pay for a pending subscription: switch the subscriber's paid subscription to it, or get the Checkout page,
     * reusing the open one or creating a session.
     *
     * @param licenseSubscriptionId the ID of the subscription in api-license
     * @param payerUserId the user paying: the subscriber, or an admin of the subscribed group
     * @return the page, or that it was switched
     */
    public CheckoutResponse checkout(UUID licenseSubscriptionId, UUID payerUserId) {
        BillingSubscription subscription = billingSubscriptionRepository
                .lockByLicenseSubscriptionId(licenseSubscriptionId)
                .filter(found -> found.getLicenseId() != null)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription with id=%s is not waiting for payment".formatted(licenseSubscriptionId)));
        Instant now = Instant.now();
        if (subscription.getStatus() != BillingSubscriptionStatus.PENDING
                || !subscription.getPendingExpiresAt().isAfter(now)) {
            throw new IllegalStateException("Subscription with id=%s is not waiting for payment"
                    .formatted(licenseSubscriptionId));
        }
        if (subscription.getPaidAt() != null) {
            // Paid, and waiting for api-license to activate it.
            return new CheckoutResponse(licenseSubscriptionId, null, null, true);
        }
        if (subscription.getStripeCheckoutSessionId() != null
                && subscription.getStripeCheckoutExpiresAt().isAfter(now.plus(REUSE_MARGIN))) {
            return toResponse(subscription);
        }

        LicenseProduct product = licenseProductRepository.lockByLicenseId(subscription.getLicenseId())
                .filter(found -> found.getSyncedAt() != null)
                .orElseThrow(() -> new IllegalStateException(
                        "Payments for this license are still being set up, try again in a minute"));
        if (!product.isPublished()) {
            // Stripe sells no inactive product; the subscription times out instead.
            throw new IllegalStateException("License with id=%s is no longer offered".formatted(product.getLicenseId()));
        }

        UserContact payer = userContactRepository.findByUserId(payerUserId)
                .filter(contact -> contact.getEmail() != null && contact.getDisabledAt() == null
                        && contact.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalStateException(
                        "Your account details have not reached payments yet, try again in a minute"));
        String customerId = customerService.ensureCustomer(subscription.getSubscriberType(),
                subscription.getSubscriberId(), payer);
        // The subscription's own list price: the license's may have changed since it was quoted.
        LicensePrice price = catalogSyncService.ensurePrice(product, subscription.getListPriceMinor());
        String couponId = ensureCoupon(subscription, product);

        Map<String, String> metadata = metadataOf(subscription, payerUserId);
        Optional<CheckoutResponse> switched = trySwitch(subscription, price, couponId, metadata);
        if (switched.isPresent()) {
            return switched.get();
        }

        StripeCheckoutSession session = stripeCheckoutClient.createSubscriptionSession(new StripeCheckoutSpec(
                customerId, price.getStripePriceId(), couponId,
                urlFor(paymentProperties.checkoutSuccessUrl(), licenseSubscriptionId),
                urlFor(paymentProperties.checkoutCancelUrl(), licenseSubscriptionId),
                sessionExpiry(subscription.getPendingExpiresAt(), now), licenseSubscriptionId.toString(), metadata));

        subscription.setStripeCustomerId(customerId);
        subscription.setStripeCheckoutSessionId(session.id());
        subscription.setStripeCheckoutUrl(session.url());
        subscription.setStripeCheckoutExpiresAt(session.expiresAt());
        log.info("Checkout started [licenseSubscriptionId={}, sessionId={}, payerUserId={}]",
                licenseSubscriptionId, session.id(), payerUserId);
        return toResponse(subscription);
    }

    // A subscriber already paying through Stripe switches that subscription to the new price: the unused part of
    // the old period is credited, a new period starts now, and the difference is charged to the saved card. If
    // the charge is declined or needs the customer, checkout takes over with a page of its own.
    private Optional<CheckoutResponse> trySwitch(BillingSubscription subscription, LicensePrice price,
                                                 String couponId, Map<String, String> metadata) {
        List<BillingSubscription> paid = billingSubscriptionRepository
                .findBySubscriberTypeAndSubscriberIdAndStatusAndStripeSubscriptionIdIsNotNull(
                        subscription.getSubscriberType(), subscription.getSubscriberId(),
                        BillingSubscriptionStatus.ACTIVE);
        Optional<BillingSubscription> current = paid.stream()
                .filter(found -> !found.getLicenseSubscriptionId().equals(subscription.getLicenseSubscriptionId()))
                .findFirst()
                .flatMap(found -> billingSubscriptionRepository.lockByLicenseSubscriptionId(
                        found.getLicenseSubscriptionId()))
                .filter(found -> found.getStripeSubscriptionId() != null
                        && found.getStatus() == BillingSubscriptionStatus.ACTIVE);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        BillingSubscription old = current.get();
        String stripeSubscriptionId = old.getStripeSubscriptionId();
        StripeSubscriptionPeriod period;
        try {
            period = stripeSubscriptionClient.switchPrice(stripeSubscriptionId, price.getStripePriceId(), couponId,
                    metadata);
        } catch (StripeIntegrationException e) {
            if (e.getStatusCode() == null || e.getStatusCode() != PAYMENT_REQUIRED) {
                throw e;
            }
            log.info("Switch charge not taken, falling back to checkout [licenseSubscriptionId={}, code={}]",
                    subscription.getLicenseSubscriptionId(), e.getStripeCode());
            return Optional.empty();
        }

        // The Stripe subscription now bills the new subscription; flushed first, as only one row may hold it.
        old.setStripeSubscriptionId(null);
        billingSubscriptionRepository.saveAndFlush(old);
        subscription.setStripeSubscriptionId(stripeSubscriptionId);
        subscription.setStripeCustomerId(old.getStripeCustomerId());
        subscription.setCancelAtPeriodEnd(false);
        subscription.setPaidAt(Instant.now());
        if (subscription.getStripeCheckoutSessionId() != null) {
            stripeCheckoutClient.expireIfOpen(subscription.getStripeCheckoutSessionId());
            subscription.setStripeCheckoutSessionId(null);
            subscription.setStripeCheckoutUrl(null);
            subscription.setStripeCheckoutExpiresAt(null);
        }
        eventPublisher.subscriptionActivated(subscription.getLicenseSubscriptionId(), period);
        log.info("Subscription switched with proration [licenseSubscriptionId={}, fromLicenseSubscriptionId={}, "
                + "stripeSubscriptionId={}]", subscription.getLicenseSubscriptionId(),
                old.getLicenseSubscriptionId(), stripeSubscriptionId);
        return Optional.of(new CheckoutResponse(subscription.getLicenseSubscriptionId(), null, null, true));
    }

    private static Map<String, String> metadataOf(BillingSubscription subscription, UUID payerUserId) {
        UUID licenseSubscriptionId = subscription.getLicenseSubscriptionId();
        return Map.of(
                "licenseSubscriptionId", licenseSubscriptionId.toString(),
                "licenseId", subscription.getLicenseId().toString(),
                "subscriberType", subscription.getSubscriberType().name(),
                "subscriberId", subscription.getSubscriberId().toString(),
                "payerUserId", payerUserId.toString());
    }

    // A promotion becomes a coupon for this subscription alone, worth exactly api-license's discount, so the
    // floor and rounding of its prices hold; one coupon per subscription, reused by later sessions.
    private String ensureCoupon(BillingSubscription subscription, LicenseProduct product) {
        long discountMinor = subscription.getListPriceMinor() - subscription.getFirstPriceMinor();
        if (discountMinor <= 0) {
            return null;
        }
        Long durationInMonths = subscription.getPromotionPeriods() == null ? null
                : (long) subscription.getPromotionPeriods()
                        * subscription.getBillingInterval().months();
        String couponId = "subscription_" + subscription.getLicenseSubscriptionId();
        stripeCheckoutClient.ensureCoupon(couponId, discountMinor, subscription.getCurrency(), durationInMonths,
                "%s promotion".formatted(product.getName()),
                Map.of("licenseSubscriptionId", subscription.getLicenseSubscriptionId().toString()));
        return couponId;
    }

    private static Instant sessionExpiry(Instant pendingExpiresAt, Instant now) {
        // Never past the subscription's own end, except where Stripe's minimum forces it; ending the
        // subscription expires the session anyway.
        Instant earliest = now.plus(MIN_SESSION_LIFETIME).plusSeconds(60);
        Instant latest = now.plus(MAX_SESSION_LIFETIME).minusSeconds(60);
        Instant wanted = pendingExpiresAt.isBefore(latest) ? pendingExpiresAt : latest;
        return wanted.isAfter(earliest) ? wanted : earliest;
    }

    private static String urlFor(String template, UUID licenseSubscriptionId) {
        return template.replace("{subscriptionId}", licenseSubscriptionId.toString());
    }

    private static CheckoutResponse toResponse(BillingSubscription subscription) {
        return new CheckoutResponse(subscription.getLicenseSubscriptionId(), subscription.getStripeCheckoutUrl(),
                subscription.getStripeCheckoutExpiresAt(), false);
    }
}
