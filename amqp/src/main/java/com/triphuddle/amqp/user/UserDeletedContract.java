package com.triphuddle.amqp.user;

/**
 * Names for {@link UserDeletedEvent}. It is published once to a fanout exchange, and each consuming service reads its
 * own queue bound to it: the plain queue is api-chat's, {@code PAYMENT_} api-payment's, {@code DESTINATION_}
 * api-destination's.
 */
public class UserDeletedContract {
    public static final String USER_DELETED_EXCHANGE_NAME = "user.deleted.exchange";
    public static final String USER_DELETED_QUEUE_NAME = "user.deleted.queue";
    public static final String USER_DELETED_DLQ_NAME = "user.deleted.dlq";
    public static final String USER_DELETED_PAYMENT_QUEUE_NAME = "user.deleted.payment.queue";
    public static final String USER_DELETED_PAYMENT_DLQ_NAME = "user.deleted.payment.dlq";
    public static final String USER_DELETED_DESTINATION_QUEUE_NAME = "user.deleted.destination.queue";
    public static final String USER_DELETED_DESTINATION_DLQ_NAME = "user.deleted.destination.dlq";
}
