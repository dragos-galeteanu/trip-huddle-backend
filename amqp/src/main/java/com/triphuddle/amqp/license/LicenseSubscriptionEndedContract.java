package com.triphuddle.amqp.license;

/**
 * Names for {@link LicenseSubscriptionEndedEvent}. It is published once to a fanout exchange, and each consuming
 * service reads its own queue bound to it: the plain queue is api-user's, {@code PAYMENT_} api-payment's and {@code
 * DESTINATION_} api-destination's.
 */
public class LicenseSubscriptionEndedContract {
    public static final String LICENSE_SUBSCRIPTION_ENDED_EXCHANGE_NAME = "license.subscription.ended.exchange";
    public static final String LICENSE_SUBSCRIPTION_ENDED_QUEUE_NAME = "license.subscription.ended.queue";
    public static final String LICENSE_SUBSCRIPTION_ENDED_DLQ_NAME = "license.subscription.ended.dlq";
    public static final String LICENSE_SUBSCRIPTION_ENDED_PAYMENT_QUEUE_NAME =
            "license.subscription.ended.payment.queue";
    public static final String LICENSE_SUBSCRIPTION_ENDED_PAYMENT_DLQ_NAME = "license.subscription.ended.payment.dlq";
    public static final String LICENSE_SUBSCRIPTION_ENDED_DESTINATION_QUEUE_NAME =
            "license.subscription.ended.destination.queue";
    public static final String LICENSE_SUBSCRIPTION_ENDED_DESTINATION_DLQ_NAME =
            "license.subscription.ended.destination.dlq";
}
