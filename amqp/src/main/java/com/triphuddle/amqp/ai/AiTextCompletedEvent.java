package com.triphuddle.amqp.ai;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The openai worker's answer to an {@link AiTextRequestedEvent}, success or failure.
 *
 * @param jobId the requester's ID for the job
 * @param task the task, as requested
 * @param status COMPLETED or FAILED
 * @param output the text, or the JSON matching the requested schema; null when FAILED
 * @param citations the web pages a web search answer drew on, in order; empty without web search
 * @param model the model that answered, or null when FAILED before calling one
 * @param inputTokens tokens read, for cost tracking
 * @param outputTokens tokens written, for cost tracking
 * @param attempts how many calls it took
 * @param error why it FAILED, or null
 * @param completedAt when the worker finished
 */
public record AiTextCompletedEvent(
        UUID jobId,
        String task,
        AiJobStatus status,
        String output,
        List<AiCitation> citations,
        String model,
        long inputTokens,
        long outputTokens,
        int attempts,
        String error,
        Instant completedAt
) {
}
