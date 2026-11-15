package com.triphuddle.amqp.license;

/**
 * Queue and dead-letter queue names for {@link LicenseSubscriptionCanceledEvent}.
 */
public class LicenseSubscriptionCanceledContract {
    public static final String LICENSE_SUBSCRIPTION_CANCELED_QUEUE_NAME = "license.subscription.canceled.queue";
    public static final String LICENSE_SUBSCRIPTION_CANCELED_DLQ_NAME = "license.subscription.canceled.dlq";
}
