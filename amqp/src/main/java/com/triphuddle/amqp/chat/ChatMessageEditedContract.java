package com.triphuddle.amqp.chat;

/**
 * Queue and dead-letter queue names for {@link ChatMessageEditedEvent}.
 */
public class ChatMessageEditedContract {
    public static final String CHAT_MESSAGE_EDITED_QUEUE_NAME = "chat.message.edited.queue";
    public static final String CHAT_MESSAGE_EDITED_DLQ_NAME = "chat.message.edited.dlq";
}
