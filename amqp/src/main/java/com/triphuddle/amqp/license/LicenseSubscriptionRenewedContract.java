package com.triphuddle.amqp.license;

/**
 * Queue and dead-letter queue names for {@link LicenseSubscriptionRenewedEvent}.
 */
public class LicenseSubscriptionRenewedContract {
    public static final String LICENSE_SUBSCRIPTION_RENEWED_QUEUE_NAME = "license.subscription.renewed.queue";
    public static final String LICENSE_SUBSCRIPTION_RENEWED_DLQ_NAME = "license.subscription.renewed.dlq";
}
