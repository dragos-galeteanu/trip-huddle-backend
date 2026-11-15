package com.triphuddle.openai.client;

import com.triphuddle.amqp.ai.AiCitation;

import java.util.List;

/**
 * What the Responses API answered.
 *
 * @param output the text, or the JSON when a schema was given; null when cut short or refused
 * @param citations the web pages the answer drew on, without repeats, in order of first use
 * @param model the model that answered
 * @param inputTokens tokens read
 * @param outputTokens tokens written
 * @param isIncomplete whether it stopped early, e.g. at the token limit
 * @param refusal the model's refusal, or null
 */
public record TextResult(
        String output,
        List<AiCitation> citations,
        String model,
        long inputTokens,
        long outputTokens,
        boolean isIncomplete,
        String refusal
) {
}
