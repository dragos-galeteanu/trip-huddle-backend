package com.triphuddle.amqp.user;

/**
 * Queue and dead-letter queue names for {@link UserContactUpdatedEvent}.
 */
public class UserContactUpdatedContract {
    public static final String USER_CONTACT_UPDATED_QUEUE_NAME = "user.contact.updated.queue";
    public static final String USER_CONTACT_UPDATED_DLQ_NAME = "user.contact.updated.dlq";
}
