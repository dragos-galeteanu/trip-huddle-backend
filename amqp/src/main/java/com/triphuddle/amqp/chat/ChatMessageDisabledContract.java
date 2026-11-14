package com.triphuddle.amqp.chat;

/**
 * Queue and dead-letter queue names for {@link ChatMessageDisabledEvent}.
 */
public class ChatMessageDisabledContract {
    public static final String CHAT_MESSAGE_DISABLED_QUEUE_NAME = "chat.message.disabled.queue";
    public static final String CHAT_MESSAGE_DISABLED_DLQ_NAME = "chat.message.disabled.dlq";
}
