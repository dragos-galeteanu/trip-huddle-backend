package com.triphuddle.amqp.payment;

/**
 * Queue and dead-letter queue names for {@link PaymentSubscriptionPastDueEvent}.
 */
public class PaymentSubscriptionPastDueContract {
    public static final String PAYMENT_SUBSCRIPTION_PAST_DUE_QUEUE_NAME = "payment.subscription.past.due.queue";
    public static final String PAYMENT_SUBSCRIPTION_PAST_DUE_DLQ_NAME = "payment.subscription.past.due.dlq";
}
