package com.triphuddle.amqp.license;

/**
 * Queue and dead-letter queue names for {@link LicenseSubscriptionActivatedEvent}. The event goes to two queues, one per
 * consuming service, as a queue has only one consumer: the first is api-user's, the {@code PAYMENT_} one api-payment's.
 */
public class LicenseSubscriptionActivatedContract {
    public static final String LICENSE_SUBSCRIPTION_ACTIVATED_QUEUE_NAME = "license.subscription.activated.queue";
    public static final String LICENSE_SUBSCRIPTION_ACTIVATED_DLQ_NAME = "license.subscription.activated.dlq";
    public static final String LICENSE_SUBSCRIPTION_ACTIVATED_PAYMENT_QUEUE_NAME = "license.subscription.activated.payment.queue";
    public static final String LICENSE_SUBSCRIPTION_ACTIVATED_PAYMENT_DLQ_NAME = "license.subscription.activated.payment.dlq";
}
