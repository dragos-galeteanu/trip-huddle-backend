package com.triphuddle.amqp.license;

/**
 * Queue and dead-letter queue names for {@link LicenseSubscriptionEndedEvent}. The event goes to two queues, one per
 * consuming service, as a queue has only one consumer: the first is api-user's, the {@code PAYMENT_} one api-payment's.
 */
public class LicenseSubscriptionEndedContract {
    public static final String LICENSE_SUBSCRIPTION_ENDED_QUEUE_NAME = "license.subscription.ended.queue";
    public static final String LICENSE_SUBSCRIPTION_ENDED_DLQ_NAME = "license.subscription.ended.dlq";
    public static final String LICENSE_SUBSCRIPTION_ENDED_PAYMENT_QUEUE_NAME = "license.subscription.ended.payment.queue";
    public static final String LICENSE_SUBSCRIPTION_ENDED_PAYMENT_DLQ_NAME = "license.subscription.ended.payment.dlq";
}
