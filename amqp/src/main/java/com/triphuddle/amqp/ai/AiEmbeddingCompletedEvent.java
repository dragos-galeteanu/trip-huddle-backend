package com.triphuddle.amqp.ai;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The openai worker's answer to an {@link AiEmbeddingRequestedEvent}, success or failure.
 *
 * @param jobId the requester's ID for the job
 * @param task the task, as requested
 * @param status COMPLETED or FAILED
 * @param vectors one embedding per input, in the inputs' order; empty when FAILED
 * @param model the model that answered, or null when FAILED before calling one
 * @param inputTokens tokens read, for cost tracking
 * @param error why it FAILED, or null
 * @param completedAt when the worker finished
 */
public record AiEmbeddingCompletedEvent(
        UUID jobId,
        String task,
        AiJobStatus status,
        List<float[]> vectors,
        String model,
        long inputTokens,
        String error,
        Instant completedAt
) {
}
