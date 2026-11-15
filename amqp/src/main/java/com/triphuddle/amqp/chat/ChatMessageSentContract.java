package com.triphuddle.amqp.chat;

/**
 * Queue and dead-letter queue names for {@link ChatMessageSentEvent}.
 */
public class ChatMessageSentContract {
    public static final String CHAT_MESSAGE_SENT_QUEUE_NAME = "chat.message.sent.queue";
    public static final String CHAT_MESSAGE_SENT_DLQ_NAME = "chat.message.sent.dlq";
}
