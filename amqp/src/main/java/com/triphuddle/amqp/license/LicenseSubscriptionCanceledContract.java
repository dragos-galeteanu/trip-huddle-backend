package com.triphuddle.amqp.license;

/**
 * Names for {@link LicenseSubscriptionCanceledEvent}. It is published once to a fanout exchange, and each consuming
 * service reads its own queue bound to it: the plain queue is api-user's, {@code PAYMENT_} api-payment's.
 */
public class LicenseSubscriptionCanceledContract {
    public static final String LICENSE_SUBSCRIPTION_CANCELED_EXCHANGE_NAME = "license.subscription.canceled.exchange";
    public static final String LICENSE_SUBSCRIPTION_CANCELED_QUEUE_NAME = "license.subscription.canceled.queue";
    public static final String LICENSE_SUBSCRIPTION_CANCELED_DLQ_NAME = "license.subscription.canceled.dlq";
    public static final String LICENSE_SUBSCRIPTION_CANCELED_PAYMENT_QUEUE_NAME =
            "license.subscription.canceled.payment.queue";
    public static final String LICENSE_SUBSCRIPTION_CANCELED_PAYMENT_DLQ_NAME =
            "license.subscription.canceled.payment.dlq";
}
