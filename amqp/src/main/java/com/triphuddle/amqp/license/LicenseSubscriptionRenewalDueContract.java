package com.triphuddle.amqp.license;

/**
 * Queue and dead-letter queue names for {@link LicenseSubscriptionRenewalDueEvent}.
 */
public class LicenseSubscriptionRenewalDueContract {
    public static final String LICENSE_SUBSCRIPTION_RENEWAL_DUE_QUEUE_NAME = "license.subscription.renewal-due.queue";
    public static final String LICENSE_SUBSCRIPTION_RENEWAL_DUE_DLQ_NAME = "license.subscription.renewal-due.dlq";
}
