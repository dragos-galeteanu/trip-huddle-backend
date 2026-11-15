package com.triphuddle.amqp.user;

/**
 * Queue and dead-letter queue names for {@link UserDisabledEvent}. The event goes to two queues, one per consuming
 * service, as a queue has only one consumer: the first is api-chat's, the {@code PAYMENT_} one api-payment's.
 */
public class UserDisabledContract {
    public static final String USER_DISABLED_QUEUE_NAME = "user.disabled.queue";
    public static final String USER_DISABLED_DLQ_NAME = "user.disabled.dlq";
    public static final String USER_DISABLED_PAYMENT_QUEUE_NAME = "user.disabled.payment.queue";
    public static final String USER_DISABLED_PAYMENT_DLQ_NAME = "user.disabled.payment.dlq";
}
