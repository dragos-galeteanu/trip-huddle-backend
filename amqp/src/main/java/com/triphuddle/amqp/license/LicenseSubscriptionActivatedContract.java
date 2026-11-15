package com.triphuddle.amqp.license;

/**
 * Queue and dead-letter queue names for {@link LicenseSubscriptionActivatedEvent}.
 */
public class LicenseSubscriptionActivatedContract {
    public static final String LICENSE_SUBSCRIPTION_ACTIVATED_QUEUE_NAME = "license.subscription.activated.queue";
    public static final String LICENSE_SUBSCRIPTION_ACTIVATED_DLQ_NAME = "license.subscription.activated.dlq";
}
