package com.triphuddle.amqp.payment;

/**
 * Queue and dead-letter queue names for {@link PaymentSubscriptionActivatedEvent}.
 */
public class PaymentSubscriptionActivatedContract {
    public static final String PAYMENT_SUBSCRIPTION_ACTIVATED_QUEUE_NAME = "payment.subscription.activated.queue";
    public static final String PAYMENT_SUBSCRIPTION_ACTIVATED_DLQ_NAME = "payment.subscription.activated.dlq";
}
