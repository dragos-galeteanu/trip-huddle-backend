package com.triphuddle.amqp.payment;

/**
 * Queue and dead-letter queue names for {@link PaymentSubscriptionEndedEvent}.
 */
public class PaymentSubscriptionEndedContract {
    public static final String PAYMENT_SUBSCRIPTION_ENDED_QUEUE_NAME = "payment.subscription.ended.queue";
    public static final String PAYMENT_SUBSCRIPTION_ENDED_DLQ_NAME = "payment.subscription.ended.dlq";
}
