package com.triphuddle.amqp.ai;

import java.util.List;
import java.util.stream.Stream;

/**
 * Every queue the AI contracts define, declared on the broker by {@code RabbitMQConfig}.
 */
public class AiQueues {

    /**
     * The queues requesters read the worker's answers from, one per requesting service; the worker answers only to
     * these. A service that requests AI adds its own here, e.g. {@code ai.text.completed.destination.queue}.
     */
    public static final List<String> REPLY_QUEUE_NAMES = List.of(
            AiTextCompletedContract.AI_TEXT_COMPLETED_DESTINATION_QUEUE_NAME,
            AiEmbeddingCompletedContract.AI_EMBEDDING_COMPLETED_DESTINATION_QUEUE_NAME);

    public static final List<String> QUEUE_NAMES = Stream.concat(
            Stream.of(
                    AiTextRequestedContract.AI_TEXT_REQUESTED_QUEUE_NAME,
                    AiTextRequestedContract.AI_TEXT_REQUESTED_DLQ_NAME,
                    AiEmbeddingRequestedContract.AI_EMBEDDING_REQUESTED_QUEUE_NAME,
                    AiEmbeddingRequestedContract.AI_EMBEDDING_REQUESTED_DLQ_NAME,
                    AiTextCompletedContract.AI_TEXT_COMPLETED_DESTINATION_DLQ_NAME,
                    AiEmbeddingCompletedContract.AI_EMBEDDING_COMPLETED_DESTINATION_DLQ_NAME),
            REPLY_QUEUE_NAMES.stream()).toList();
}
