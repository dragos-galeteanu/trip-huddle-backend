package com.triphuddle.amqp.ai;

/**
 * Queue and dead-letter queue names for {@link AiTextRequestedEvent}, read by the openai worker.
 */
public class AiTextRequestedContract {
    public static final String AI_TEXT_REQUESTED_QUEUE_NAME = "ai.text.requested.queue";
    public static final String AI_TEXT_REQUESTED_DLQ_NAME = "ai.text.requested.dlq";
}
