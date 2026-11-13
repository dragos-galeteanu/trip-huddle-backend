package com.triphuddle.amqp.notification;

/**
 * Queue and dead-letter queue names for {@link SendEmailRequest}.
 */
public class NotificationEmailContract {
    public static final String EMAIL_QUEUE_NAME = "notification.email.queue";
    public static final String EMAIL_DLQ_NAME = "notification.email.dlq";
}
