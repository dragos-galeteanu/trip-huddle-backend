package com.triphuddle.amqp.payment;

/**
 * Queue and dead-letter queue names for {@link PaymentSubscriptionRenewedEvent}.
 */
public class PaymentSubscriptionRenewedContract {
    public static final String PAYMENT_SUBSCRIPTION_RENEWED_QUEUE_NAME = "payment.subscription.renewed.queue";
    public static final String PAYMENT_SUBSCRIPTION_RENEWED_DLQ_NAME = "payment.subscription.renewed.dlq";
}
