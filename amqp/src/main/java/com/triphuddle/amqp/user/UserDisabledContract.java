package com.triphuddle.amqp.user;

/**
 * Queue and dead-letter queue names for {@link UserDisabledEvent}.
 */
public class UserDisabledContract {
    public static final String USER_DISABLED_QUEUE_NAME = "user.disabled.queue";
    public static final String USER_DISABLED_DLQ_NAME = "user.disabled.dlq";
}
