package com.triphuddle.amqp.ai;

/**
 * The queues requesting services read {@link AiTextCompletedEvent} from, one per service, as each names its own
 * in {@link AiTextRequestedEvent#replyQueue()}.
 */
public class AiTextCompletedContract {
    public static final String AI_TEXT_COMPLETED_DESTINATION_QUEUE_NAME = "ai.text.completed.destination.queue";
    public static final String AI_TEXT_COMPLETED_DESTINATION_DLQ_NAME = "ai.text.completed.destination.dlq";
}
