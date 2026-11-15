package com.triphuddle.amqp.user;

/**
 * Queue and dead-letter queue names for {@link UserDeletedEvent}. The event goes to two queues, one per consuming
 * service, as a queue has only one consumer: the first is api-chat's, the {@code PAYMENT_} one api-payment's.
 */
public class UserDeletedContract {
    public static final String USER_DELETED_QUEUE_NAME = "user.deleted.queue";
    public static final String USER_DELETED_DLQ_NAME = "user.deleted.dlq";
    public static final String USER_DELETED_PAYMENT_QUEUE_NAME = "user.deleted.payment.queue";
    public static final String USER_DELETED_PAYMENT_DLQ_NAME = "user.deleted.payment.dlq";
}
