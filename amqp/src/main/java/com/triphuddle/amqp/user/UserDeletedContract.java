package com.triphuddle.amqp.user;

/**
 * Queue and dead-letter queue names for {@link UserDeletedEvent}.
 */
public class UserDeletedContract {
    public static final String USER_DELETED_QUEUE_NAME = "user.deleted.queue";
    public static final String USER_DELETED_DLQ_NAME = "user.deleted.dlq";
}
