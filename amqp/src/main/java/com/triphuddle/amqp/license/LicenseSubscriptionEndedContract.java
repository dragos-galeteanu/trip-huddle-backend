package com.triphuddle.amqp.license;

/**
 * Queue and dead-letter queue names for {@link LicenseSubscriptionEndedEvent}.
 */
public class LicenseSubscriptionEndedContract {
    public static final String LICENSE_SUBSCRIPTION_ENDED_QUEUE_NAME = "license.subscription.ended.queue";
    public static final String LICENSE_SUBSCRIPTION_ENDED_DLQ_NAME = "license.subscription.ended.dlq";
}
