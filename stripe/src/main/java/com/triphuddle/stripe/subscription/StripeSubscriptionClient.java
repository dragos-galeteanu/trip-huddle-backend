package com.triphuddle.stripe.subscription;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.param.SubscriptionCancelParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.common.EmptyParam;
import com.triphuddle.stripe.exception.StripeIntegrationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;

/**
 * Reads, cancels and changes Stripe Subscriptions.
 */
@Slf4j
@RequiredArgsConstructor
public class StripeSubscriptionClient {

    private static final String CANCELED = "canceled";

    private final StripeClient stripeClient;

    /**
     * Read a subscription's status and current period.
     *
     * @param subscriptionId the Stripe subscription ID
     * @return the subscription
     */
    public StripeSubscriptionPeriod retrieve(String subscriptionId) {
        try {
            return toPeriod(stripeClient.v1().subscriptions().retrieve(subscriptionId));
        } catch (StripeException e) {
            throw new StripeIntegrationException("Reading Stripe subscription %s failed".formatted(subscriptionId), e);
        }
    }

    /**
     * Cancel a subscription now, without crediting or invoicing the rest of its period; a canceled one is left.
     *
     * @param subscriptionId the Stripe subscription ID
     * @return true if it was running and is now canceled
     */
    public boolean cancelNow(String subscriptionId) {
        try {
            Subscription subscription = stripeClient.v1().subscriptions().retrieve(subscriptionId);
            if (CANCELED.equals(subscription.getStatus())) {
                return false;
            }
            stripeClient.v1().subscriptions().cancel(subscriptionId, SubscriptionCancelParams.builder()
                    .setProrate(false)
                    .setInvoiceNow(false)
                    .build());
            log.info("Stripe subscription canceled [subscriptionId={}]", subscriptionId);
            return true;
        } catch (StripeException e) {
            throw new StripeIntegrationException("Canceling Stripe subscription %s failed".formatted(subscriptionId),
                    e);
        }
    }

    /**
     * Make a subscription stop, or keep going, at the end of its current period.
     *
     * @param subscriptionId the Stripe subscription ID
     * @param isCancelAtPeriodEnd true to stop at the period end, false to keep renewing
     * @return the subscription after the change
     */
    public StripeSubscriptionPeriod setCancelAtPeriodEnd(String subscriptionId, boolean isCancelAtPeriodEnd) {
        try {
            Subscription updated = stripeClient.v1().subscriptions().update(subscriptionId,
                    SubscriptionUpdateParams.builder().setCancelAtPeriodEnd(isCancelAtPeriodEnd).build());
            log.info("Stripe subscription renewal changed [subscriptionId={}, isCancelAtPeriodEnd={}]",
                    subscriptionId, isCancelAtPeriodEnd);
            return toPeriod(updated);
        } catch (StripeException e) {
            throw new StripeIntegrationException("Changing renewal of Stripe subscription %s failed"
                    .formatted(subscriptionId), e);
        }
    }

    /**
     * Move a subscription to another price at once: a new period starts now, the unused part of the old one is
     * credited, and the difference is charged straight away. Nothing changes if that charge fails.
     *
     * @param subscriptionId the Stripe subscription ID
     * @param priceId the new recurring price
     * @param couponId the coupon of the new subscription, or null to drop any discount
     * @param metadata key-values replacing the subscription's own
     * @return the subscription after the change
     * @throws StripeIntegrationException with status 402 if the charge was declined or needs the customer
     */
    public StripeSubscriptionPeriod switchPrice(String subscriptionId, String priceId, String couponId,
                                                Map<String, String> metadata) {
        try {
            Subscription current = stripeClient.v1().subscriptions().retrieve(subscriptionId);
            String itemId = current.getItems().getData().get(0).getId();
            SubscriptionUpdateParams.Builder params = SubscriptionUpdateParams.builder()
                    .addItem(SubscriptionUpdateParams.Item.builder().setId(itemId).setPrice(priceId).build())
                    .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.ALWAYS_INVOICE)
                    .setBillingCycleAnchor(SubscriptionUpdateParams.BillingCycleAnchor.builder()
                            .setType(SubscriptionUpdateParams.BillingCycleAnchor.Type.NOW).build())
                    // Refuses the whole change when the charge doesn't go through, rather than leaving it half done.
                    .setPaymentBehavior(SubscriptionUpdateParams.PaymentBehavior.ERROR_IF_INCOMPLETE)
                    .setCancelAtPeriodEnd(false)
                    .setMetadata(metadata);
            if (couponId == null) {
                params.setDiscounts(EmptyParam.EMPTY);
            } else {
                params.addDiscount(SubscriptionUpdateParams.Discount.builder().setCoupon(couponId).build());
            }
            Subscription updated = stripeClient.v1().subscriptions().update(subscriptionId, params.build());
            log.info("Stripe subscription switched [subscriptionId={}, priceId={}]", subscriptionId, priceId);
            return toPeriod(updated);
        } catch (StripeException e) {
            throw new StripeIntegrationException("Switching Stripe subscription %s failed".formatted(subscriptionId),
                    e);
        }
    }

    private static StripeSubscriptionPeriod toPeriod(Subscription subscription) {
        // Periods live on the items since the 2025 API versions; ours have exactly one item.
        SubscriptionItem item = subscription.getItems().getData().get(0);
        return new StripeSubscriptionPeriod(subscription.getId(), subscription.getStatus(),
                Instant.ofEpochSecond(item.getCurrentPeriodStart()), Instant.ofEpochSecond(item.getCurrentPeriodEnd()),
                Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()),
                subscription.getMetadata() == null ? Map.of() : Map.copyOf(subscription.getMetadata()));
    }
}
