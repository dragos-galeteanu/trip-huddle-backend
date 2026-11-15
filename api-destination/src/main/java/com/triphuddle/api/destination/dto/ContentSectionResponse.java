package com.triphuddle.api.destination.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.amqp.ai.AiCitation;
import com.triphuddle.api.destination.entity.ContentSection;

import java.time.Instant;
import java.util.List;

/**
 * One section of a place's AI content.
 *
 * @param section OVERVIEW, OPINIONS or PRACTICAL
 * @param state READY, GENERATING (ask again shortly), FAILED, or UNAVAILABLE (the day's AI budget or the reader's
 *              share is spent)
 * @param languageCode the language of {@code content}
 * @param isFallback whether it is the English version while the asked language is generated
 * @param isRefreshing whether a newer version is being generated
 * @param content the section's JSON, shaped by its schema, or null
 * @param citations the web pages it draws on, for OPINIONS
 * @param model the model that wrote it
 * @param generatedAt when it was written
 * @param isAiGenerated always true, for the label the frontend must show
 */
public record ContentSectionResponse(
        ContentSection section,
        String state,
        String languageCode,
        boolean isFallback,
        boolean isRefreshing,
        JsonNode content,
        List<AiCitation> citations,
        String model,
        Instant generatedAt,
        boolean isAiGenerated
) {
}
