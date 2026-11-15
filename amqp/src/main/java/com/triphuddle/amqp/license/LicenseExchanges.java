package com.triphuddle.amqp.license;

import java.util.List;
import java.util.Map;

/**
 * Every license exchange and the queues bound to it, one per consuming service.
 * {@code RabbitMQConfig} declares each as a durable fanout exchange with these queues bound to it.
 */
public class LicenseExchanges {
    public static final Map<String, List<String>> BINDINGS = Map.of(
            LicenseSubscriptionActivatedContract.LICENSE_SUBSCRIPTION_ACTIVATED_EXCHANGE_NAME, List.of(
                    LicenseSubscriptionActivatedContract.LICENSE_SUBSCRIPTION_ACTIVATED_QUEUE_NAME,
                    LicenseSubscriptionActivatedContract.LICENSE_SUBSCRIPTION_ACTIVATED_PAYMENT_QUEUE_NAME,
                    LicenseSubscriptionActivatedContract.LICENSE_SUBSCRIPTION_ACTIVATED_DESTINATION_QUEUE_NAME),
            LicenseSubscriptionCanceledContract.LICENSE_SUBSCRIPTION_CANCELED_EXCHANGE_NAME, List.of(
                    LicenseSubscriptionCanceledContract.LICENSE_SUBSCRIPTION_CANCELED_QUEUE_NAME,
                    LicenseSubscriptionCanceledContract.LICENSE_SUBSCRIPTION_CANCELED_PAYMENT_QUEUE_NAME),
            LicenseSubscriptionEndedContract.LICENSE_SUBSCRIPTION_ENDED_EXCHANGE_NAME, List.of(
                    LicenseSubscriptionEndedContract.LICENSE_SUBSCRIPTION_ENDED_QUEUE_NAME,
                    LicenseSubscriptionEndedContract.LICENSE_SUBSCRIPTION_ENDED_PAYMENT_QUEUE_NAME,
                    LicenseSubscriptionEndedContract.LICENSE_SUBSCRIPTION_ENDED_DESTINATION_QUEUE_NAME)
    );
}
