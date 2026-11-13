package com.triphuddle.amqp.user;

/**
 * Queue and dead-letter queue names for {@link UserRegisteredEvent}.
 */
public class UserRegisteredContract {
    public static final String USER_REGISTERED_QUEUE_NAME = "user.registered.queue";
    public static final String USER_REGISTERED_DLQ_NAME = "user.registered.dlq";
}
