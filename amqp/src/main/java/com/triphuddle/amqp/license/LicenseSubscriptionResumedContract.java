package com.triphuddle.amqp.license;

/**
 * Queue and dead-letter queue names for {@link LicenseSubscriptionResumedEvent}.
 */
public class LicenseSubscriptionResumedContract {
    public static final String LICENSE_SUBSCRIPTION_RESUMED_QUEUE_NAME = "license.subscription.resumed.queue";
    public static final String LICENSE_SUBSCRIPTION_RESUMED_DLQ_NAME = "license.subscription.resumed.dlq";
}
