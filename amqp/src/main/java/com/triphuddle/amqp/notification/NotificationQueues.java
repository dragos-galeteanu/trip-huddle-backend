package com.triphuddle.amqp.notification;

import java.util.List;

/** Every queue the notification contracts define, declared on the broker by {@code RabbitMQConfig}. */
public class NotificationQueues {
    public static final List<String> QUEUE_NAMES = List.of(
            NotificationEmailContract.EMAIL_QUEUE_NAME,
            NotificationEmailContract.EMAIL_DLQ_NAME
    );
}
