package com.triphuddle.amqp.ai;

import java.util.UUID;

/**
 * Asks the openai worker for text, as JSON matching a schema when one is given; the answer is an
 * {@link AiTextCompletedEvent} on {@code replyQueue}.
 *
 * @param jobId the requester's ID for the job, echoed in the reply
 * @param replyQueue where the reply goes; one of {@link AiQueues#REPLY_QUEUE_NAMES}
 * @param task what it is for, e.g. {@code place-content}; picks the model when {@code model} is null
 * @param model the OpenAI model, or null for the task's configured one
 * @param systemPrompt the instructions, or null
 * @param userPrompt the request; required
 * @param schemaName a name for the JSON schema, e.g. {@code place_overview}; required with a schema
 * @param jsonSchema a JSON schema the answer must match, in strict mode, or null for plain text
 * @param isWebSearch whether the model may search the web, returning the sources it used as citations
 * @param maxOutputTokens the most tokens to generate, or null for the worker's default
 */
public record AiTextRequestedEvent(
        UUID jobId,
        String replyQueue,
        String task,
        String model,
        String systemPrompt,
        String userPrompt,
        String schemaName,
        String jsonSchema,
        boolean isWebSearch,
        Integer maxOutputTokens
) {
}
