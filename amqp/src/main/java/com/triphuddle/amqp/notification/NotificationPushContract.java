package com.triphuddle.amqp.notification;

/**
 * Queue and dead-letter queue names for {@link SendPushRequest}.
 */
public class NotificationPushContract {
    public static final String PUSH_QUEUE_NAME = "notification.push.queue";
    public static final String PUSH_DLQ_NAME = "notification.push.dlq";
}
