package com.triphuddle.amqp.license;

/**
 * Names for {@link LicenseSubscriptionActivatedEvent}. It is published once to a fanout exchange, and each consuming
 * service reads its own queue bound to it: the plain queue is api-user's, {@code PAYMENT_} api-payment's and {@code
 * DESTINATION_} api-destination's.
 */
public class LicenseSubscriptionActivatedContract {
    public static final String LICENSE_SUBSCRIPTION_ACTIVATED_EXCHANGE_NAME = "license.subscription.activated.exchange";
    public static final String LICENSE_SUBSCRIPTION_ACTIVATED_QUEUE_NAME = "license.subscription.activated.queue";
    public static final String LICENSE_SUBSCRIPTION_ACTIVATED_DLQ_NAME = "license.subscription.activated.dlq";
    public static final String LICENSE_SUBSCRIPTION_ACTIVATED_PAYMENT_QUEUE_NAME =
            "license.subscription.activated.payment.queue";
    public static final String LICENSE_SUBSCRIPTION_ACTIVATED_PAYMENT_DLQ_NAME =
            "license.subscription.activated.payment.dlq";
    public static final String LICENSE_SUBSCRIPTION_ACTIVATED_DESTINATION_QUEUE_NAME =
            "license.subscription.activated.destination.queue";
    public static final String LICENSE_SUBSCRIPTION_ACTIVATED_DESTINATION_DLQ_NAME =
            "license.subscription.activated.destination.dlq";
}
