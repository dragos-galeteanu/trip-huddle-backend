package com.triphuddle.amqp.payment;

import java.util.List;

/** Every queue the payment contracts define, declared on the broker by {@code RabbitMQConfig}. */
public class PaymentQueues {
    public static final List<String> QUEUE_NAMES = List.of(
            PaymentSubscriptionActivatedContract.PAYMENT_SUBSCRIPTION_ACTIVATED_QUEUE_NAME,
            PaymentSubscriptionActivatedContract.PAYMENT_SUBSCRIPTION_ACTIVATED_DLQ_NAME,
            PaymentSubscriptionRenewedContract.PAYMENT_SUBSCRIPTION_RENEWED_QUEUE_NAME,
            PaymentSubscriptionRenewedContract.PAYMENT_SUBSCRIPTION_RENEWED_DLQ_NAME,
            PaymentSubscriptionPastDueContract.PAYMENT_SUBSCRIPTION_PAST_DUE_QUEUE_NAME,
            PaymentSubscriptionPastDueContract.PAYMENT_SUBSCRIPTION_PAST_DUE_DLQ_NAME,
            PaymentSubscriptionEndedContract.PAYMENT_SUBSCRIPTION_ENDED_QUEUE_NAME,
            PaymentSubscriptionEndedContract.PAYMENT_SUBSCRIPTION_ENDED_DLQ_NAME,
            PaymentSubscriptionRenewalChangedContract.PAYMENT_SUBSCRIPTION_RENEWAL_CHANGED_QUEUE_NAME,
            PaymentSubscriptionRenewalChangedContract.PAYMENT_SUBSCRIPTION_RENEWAL_CHANGED_DLQ_NAME
    );
}
