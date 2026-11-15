package com.triphuddle.stripe.checkout;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.CouponCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionExpireParams;
import com.triphuddle.stripe.exception.StripeIntegrationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * Creates subscription Checkout Sessions with Stripe Tax, their one-off coupons, and expires sessions.
 */
@Slf4j
@RequiredArgsConstructor
public class StripeCheckoutClient {

    private static final String RESOURCE_ALREADY_EXISTS = "resource_already_exists";

    private final StripeClient stripeClient;

    /**
     * Create an amount-off coupon with our own ID, or keep the one that exists with it.
     *
     * @param id our ID for it, unique to what it discounts
     * @param amountOffMinor the discount per invoice, in minor units
     * @param currency the ISO 4217 currency code
     * @param durationInMonths how many months of invoices it discounts, or null for all of them
     * @param name the name shown on checkout and invoices
     * @param metadata extra key-values to store on it
     */
    public void ensureCoupon(String id, long amountOffMinor, String currency, Long durationInMonths, String name,
                             Map<String, String> metadata) {
        CouponCreateParams.Builder params = CouponCreateParams.builder()
                .setId(id)
                .setName(name)
                .setAmountOff(amountOffMinor)
                .setCurrency(currency.toLowerCase(Locale.ROOT))
                // Only the checkout it was made for may use it.
                .setMaxRedemptions(1L);
        if (durationInMonths == null) {
            params.setDuration(CouponCreateParams.Duration.FOREVER);
        } else {
            params.setDuration(CouponCreateParams.Duration.REPEATING).setDurationInMonths(durationInMonths);
        }
        metadata.forEach(params::putMetadata);
        try {
            stripeClient.v1().coupons().create(params.build());
            log.info("Stripe coupon created [couponId={}, amountOffMinor={}, durationInMonths={}]", id,
                    amountOffMinor, durationInMonths);
        } catch (StripeException e) {
            // Its terms come from the subscription it was made for, which don't change.
            if (!RESOURCE_ALREADY_EXISTS.equals(e.getCode())) {
                throw new StripeIntegrationException("Creating Stripe coupon %s failed".formatted(id), e);
            }
        }
    }

    /**
     * Create a Checkout Session for a subscription, with tax, a required billing address and tax ID collection.
     *
     * @param spec the session
     * @return the session
     */
    public StripeCheckoutSession createSubscriptionSession(StripeCheckoutSpec spec) {
        SessionCreateParams.SubscriptionData.Builder subscriptionData = SessionCreateParams.SubscriptionData.builder();
        spec.metadata().forEach(subscriptionData::putMetadata);
        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(spec.customerId())
                .addLineItem(SessionCreateParams.LineItem.builder().setPrice(spec.priceId()).setQuantity(1L).build())
                // Cards bring Apple Pay and Google Pay with them.
                .addAllowedPaymentMethodType(SessionCreateParams.AllowedPaymentMethodType.CARD)
                .addAllowedPaymentMethodType(SessionCreateParams.AllowedPaymentMethodType.LINK)
                .setAutomaticTax(SessionCreateParams.AutomaticTax.builder().setEnabled(true).build())
                .setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.REQUIRED)
                // Saves the address and business name on the customer, for tax on later invoices.
                .setCustomerUpdate(SessionCreateParams.CustomerUpdate.builder()
                        .setAddress(SessionCreateParams.CustomerUpdate.Address.AUTO)
                        .setName(SessionCreateParams.CustomerUpdate.Name.AUTO)
                        .build())
                .setTaxIdCollection(SessionCreateParams.TaxIdCollection.builder().setEnabled(true).build())
                .setSuccessUrl(spec.successUrl())
                .setCancelUrl(spec.cancelUrl())
                .setExpiresAt(spec.expiresAt().getEpochSecond())
                .setClientReferenceId(spec.clientReferenceId())
                .setSubscriptionData(subscriptionData.build());
        if (spec.couponId() != null) {
            params.addDiscount(SessionCreateParams.Discount.builder().setCoupon(spec.couponId()).build());
        } else {
            // Stripe's own code box is off: only our promotions apply, as coupons we attach.
            params.setAllowPromotionCodes(false);
        }
        spec.metadata().forEach(params::putMetadata);
        try {
            Session session = stripeClient.v1().checkout().sessions().create(params.build());
            log.info("Stripe checkout session created [sessionId={}, customerId={}]", session.getId(),
                    spec.customerId());
            return new StripeCheckoutSession(session.getId(), session.getUrl(),
                    Instant.ofEpochSecond(session.getExpiresAt()));
        } catch (StripeException e) {
            throw new StripeIntegrationException("Creating Stripe checkout session failed", e);
        }
    }

    /**
     * Read a session's state, e.g. to find a payment whose event never arrived.
     *
     * @param sessionId the Stripe session ID
     * @return the session's state
     */
    public StripeCheckoutSessionState retrieve(String sessionId) {
        try {
            Session session = stripeClient.v1().checkout().sessions().retrieve(sessionId);
            return new StripeCheckoutSessionState(session.getId(), session.getStatus(), session.getPaymentStatus(),
                    session.getCustomer(), session.getSubscription());
        } catch (StripeException e) {
            throw new StripeIntegrationException("Reading Stripe checkout session %s failed".formatted(sessionId), e);
        }
    }

    /**
     * Expire a session so it can no longer be paid; one that is no longer open is left as it is.
     *
     * @param sessionId the Stripe session ID
     * @return true if it was open and is now expired
     */
    public boolean expireIfOpen(String sessionId) {
        try {
            Session session = stripeClient.v1().checkout().sessions().retrieve(sessionId);
            if (!"open".equals(session.getStatus())) {
                return false;
            }
            stripeClient.v1().checkout().sessions().expire(sessionId,
                    SessionExpireParams.builder().build());
            log.info("Stripe checkout session expired [sessionId={}]", sessionId);
            return true;
        } catch (StripeException e) {
            throw new StripeIntegrationException("Expiring Stripe checkout session %s failed".formatted(sessionId), e);
        }
    }
}
