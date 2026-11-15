package com.triphuddle.stripe.refund;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.triphuddle.stripe.exception.StripeIntegrationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;

/**
 * Gives money back from Stripe payments.
 */
@Slf4j
@RequiredArgsConstructor
public class StripeRefundClient {

    private final StripeClient stripeClient;

    /**
     * Refund part or all of a payment; repeating the call with the same key returns the same refund.
     *
     * @param paymentIntentId the payment intent to refund from
     * @param amountMinor how much to refund, in minor units
     * @param isRequestedByCustomer true to mark it requested by the customer, false to give no reason
     * @param metadata key-values to store on it
     * @param idempotencyKey a key unique to this refund
     * @return the refund
     */
    public StripeRefund create(String paymentIntentId, long amountMinor, boolean isRequestedByCustomer,
                               Map<String, String> metadata, String idempotencyKey) {
        RefundCreateParams.Builder params = RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .setAmount(amountMinor);
        if (isRequestedByCustomer) {
            params.setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER);
        }
        metadata.forEach(params::putMetadata);
        try {
            Refund refund = stripeClient.v1().refunds().create(params.build(),
                    RequestOptions.builder().setIdempotencyKey(idempotencyKey).build());
            log.info("Stripe refund created [refundId={}, paymentIntentId={}, amountMinor={}, status={}]",
                    refund.getId(), paymentIntentId, amountMinor, refund.getStatus());
            return of(refund);
        } catch (StripeException e) {
            throw new StripeIntegrationException("Refunding Stripe payment %s failed".formatted(paymentIntentId), e);
        }
    }

    /**
     * Convert Stripe's refund model, e.g. from a webhook event.
     *
     * @param refund the refund
     * @return the refund
     */
    public static StripeRefund of(Refund refund) {
        return new StripeRefund(refund.getId(), refund.getPaymentIntent(),
                refund.getAmount() == null ? 0 : refund.getAmount(), refund.getCurrency(), refund.getStatus(),
                refund.getReason(),
                refund.getCreated() == null ? Instant.now() : Instant.ofEpochSecond(refund.getCreated()),
                refund.getMetadata() == null ? Map.of() : Map.copyOf(refund.getMetadata()));
    }
}
