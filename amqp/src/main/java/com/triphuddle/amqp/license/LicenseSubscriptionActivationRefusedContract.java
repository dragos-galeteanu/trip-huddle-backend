package com.triphuddle.amqp.license;

/**
 * Queue and dead-letter queue names for {@link LicenseSubscriptionActivationRefusedEvent}.
 */
public class LicenseSubscriptionActivationRefusedContract {
    public static final String LICENSE_SUBSCRIPTION_ACTIVATION_REFUSED_QUEUE_NAME =
            "license.subscription.activation.refused.queue";
    public static final String LICENSE_SUBSCRIPTION_ACTIVATION_REFUSED_DLQ_NAME =
            "license.subscription.activation.refused.dlq";
}
