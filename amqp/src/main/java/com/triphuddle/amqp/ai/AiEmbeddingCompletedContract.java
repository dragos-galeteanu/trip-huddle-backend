package com.triphuddle.amqp.ai;

/**
 * The queues requesting services read {@link AiEmbeddingCompletedEvent} from, one per service, as each names its
 * own in {@link AiEmbeddingRequestedEvent#replyQueue()}.
 */
public class AiEmbeddingCompletedContract {
    public static final String AI_EMBEDDING_COMPLETED_DESTINATION_QUEUE_NAME =
            "ai.embedding.completed.destination.queue";
    public static final String AI_EMBEDDING_COMPLETED_DESTINATION_DLQ_NAME = "ai.embedding.completed.destination.dlq";
}
