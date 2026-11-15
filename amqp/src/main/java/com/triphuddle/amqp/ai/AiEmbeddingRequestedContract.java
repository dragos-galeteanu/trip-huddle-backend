package com.triphuddle.amqp.ai;

/**
 * Queue and dead-letter queue names for {@link AiEmbeddingRequestedEvent}, read by the openai worker.
 */
public class AiEmbeddingRequestedContract {
    public static final String AI_EMBEDDING_REQUESTED_QUEUE_NAME = "ai.embedding.requested.queue";
    public static final String AI_EMBEDDING_REQUESTED_DLQ_NAME = "ai.embedding.requested.dlq";
}
