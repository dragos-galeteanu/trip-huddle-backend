package com.triphuddle.amqp.chat;

import java.util.List;

/** Every queue the chat contracts define, declared on the broker by {@code RabbitMQConfig}. */
public class ChatQueues {
    public static final List<String> QUEUE_NAMES = List.of(
            ChatMessageSentContract.CHAT_MESSAGE_SENT_QUEUE_NAME,
            ChatMessageSentContract.CHAT_MESSAGE_SENT_DLQ_NAME,
            ChatMessageEditedContract.CHAT_MESSAGE_EDITED_QUEUE_NAME,
            ChatMessageEditedContract.CHAT_MESSAGE_EDITED_DLQ_NAME,
            ChatMessageDeletedContract.CHAT_MESSAGE_DELETED_QUEUE_NAME,
            ChatMessageDeletedContract.CHAT_MESSAGE_DELETED_DLQ_NAME,
            ChatMessageDisabledContract.CHAT_MESSAGE_DISABLED_QUEUE_NAME,
            ChatMessageDisabledContract.CHAT_MESSAGE_DISABLED_DLQ_NAME,
            ChatMessageReportedContract.CHAT_MESSAGE_REPORTED_QUEUE_NAME,
            ChatMessageReportedContract.CHAT_MESSAGE_REPORTED_DLQ_NAME
    );
}
