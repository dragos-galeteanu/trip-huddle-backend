package com.triphuddle.amqp.license;

/**
 * Queue and dead-letter queue names for {@link LicenseSubscriptionPendingEvent}.
 */
public class LicenseSubscriptionPendingContract {
    public static final String LICENSE_SUBSCRIPTION_PENDING_QUEUE_NAME = "license.subscription.pending.queue";
    public static final String LICENSE_SUBSCRIPTION_PENDING_DLQ_NAME = "license.subscription.pending.dlq";
}
