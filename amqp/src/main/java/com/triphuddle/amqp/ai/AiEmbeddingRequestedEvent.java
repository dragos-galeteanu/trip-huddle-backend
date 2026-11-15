package com.triphuddle.amqp.ai;

import java.util.List;
import java.util.UUID;

/**
 * Asks the openai worker for embeddings of some texts; the answer is an {@link AiEmbeddingCompletedEvent} on
 * {@code replyQueue}.
 *
 * @param jobId the requester's ID for the job, echoed in the reply
 * @param replyQueue where the reply goes; one of {@link AiQueues#REPLY_QUEUE_NAMES}
 * @param task what it is for, e.g. {@code place-search}
 * @param model the embedding model, or null for the configured one
 * @param inputs the texts, at most {@link #MAX_INPUTS}
 */
public record AiEmbeddingRequestedEvent(
        UUID jobId,
        String replyQueue,
        String task,
        String model,
        List<String> inputs
) {

    /** The most texts one request may carry. */
    public static final int MAX_INPUTS = 256;
}
