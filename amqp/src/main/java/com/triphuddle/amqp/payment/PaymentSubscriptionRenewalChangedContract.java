package com.triphuddle.amqp.payment;

/**
 * Queue and dead-letter queue names for {@link PaymentSubscriptionRenewalChangedEvent}.
 */
public class PaymentSubscriptionRenewalChangedContract {
    public static final String PAYMENT_SUBSCRIPTION_RENEWAL_CHANGED_QUEUE_NAME = "payment.subscription.renewal.changed.queue";
    public static final String PAYMENT_SUBSCRIPTION_RENEWAL_CHANGED_DLQ_NAME = "payment.subscription.renewal.changed.dlq";
}
