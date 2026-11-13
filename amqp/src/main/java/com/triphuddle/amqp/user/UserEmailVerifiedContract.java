package com.triphuddle.amqp.user;

/**
 * Queue and dead-letter queue names for {@link UserEmailVerifiedEvent}.
 */
public class UserEmailVerifiedContract {
    public static final String USER_EMAIL_VERIFIED_QUEUE_NAME = "user.email-verified.queue";
    public static final String USER_EMAIL_VERIFIED_DLQ_NAME = "user.email-verified.dlq";
}
