package com.triphuddle.amqp.chat;

/**
 * Queue and dead-letter queue names for {@link ChatMessageReportedEvent}.
 */
public class ChatMessageReportedContract {
    public static final String CHAT_MESSAGE_REPORTED_QUEUE_NAME = "chat.message.reported.queue";
    public static final String CHAT_MESSAGE_REPORTED_DLQ_NAME = "chat.message.reported.dlq";
}
