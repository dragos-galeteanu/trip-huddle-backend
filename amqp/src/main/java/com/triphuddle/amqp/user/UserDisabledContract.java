package com.triphuddle.amqp.user;

/**
 * Names for {@link UserDisabledEvent}. It is published once to a fanout exchange, and each consuming service reads its
 * own queue bound to it: the plain queue is api-chat's, {@code PAYMENT_} api-payment's.
 */
public class UserDisabledContract {
    public static final String USER_DISABLED_EXCHANGE_NAME = "user.disabled.exchange";
    public static final String USER_DISABLED_QUEUE_NAME = "user.disabled.queue";
    public static final String USER_DISABLED_DLQ_NAME = "user.disabled.dlq";
    public static final String USER_DISABLED_PAYMENT_QUEUE_NAME = "user.disabled.payment.queue";
    public static final String USER_DISABLED_PAYMENT_DLQ_NAME = "user.disabled.payment.dlq";
}
