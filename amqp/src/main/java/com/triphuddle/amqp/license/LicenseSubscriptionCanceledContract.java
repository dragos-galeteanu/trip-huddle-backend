package com.triphuddle.amqp.license;

/**
 * Queue and dead-letter queue names for {@link LicenseSubscriptionCanceledEvent}. The event goes to two queues, one per
 * consuming service, as a queue has only one consumer: the first is api-user's, the {@code PAYMENT_} one api-payment's.
 */
public class LicenseSubscriptionCanceledContract {
    public static final String LICENSE_SUBSCRIPTION_CANCELED_QUEUE_NAME = "license.subscription.canceled.queue";
    public static final String LICENSE_SUBSCRIPTION_CANCELED_DLQ_NAME = "license.subscription.canceled.dlq";
    public static final String LICENSE_SUBSCRIPTION_CANCELED_PAYMENT_QUEUE_NAME =
            "license.subscription.canceled.payment.queue";
    public static final String LICENSE_SUBSCRIPTION_CANCELED_PAYMENT_DLQ_NAME =
            "license.subscription.canceled.payment.dlq";
}
