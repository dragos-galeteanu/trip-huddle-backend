package com.triphuddle.amqp.chat;

/**
 * Queue and dead-letter queue names for {@link ChatMessageDeletedEvent}.
 */
public class ChatMessageDeletedContract {
    public static final String CHAT_MESSAGE_DELETED_QUEUE_NAME = "chat.message.deleted.queue";
    public static final String CHAT_MESSAGE_DELETED_DLQ_NAME = "chat.message.deleted.dlq";
}
