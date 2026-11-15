package com.triphuddle.api.destination.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.amqp.ai.AiCitation;

import java.time.Instant;
import java.util.List;

/**
 * The AI's explanation of a visa case.
 *
 * @param state READY, GENERATING (ask again shortly), FAILED, UNAVAILABLE (today's AI budget is spent) or
 *              NOT_NEEDED (a citizen)
 * @param content summary, requirement, maxStay, steps, documents, costs, processingTime, residencePermitNotes,
 *                officialLinks and warnings, or null
 * @param citations the pages the model read
 * @param languageCode the language it is written in
 * @param model the model that wrote it
 * @param generatedAt when it was written
 * @param isRefreshing whether a newer version is being written
 * @param isAiGenerated whether it was written by AI, for the label the frontend shows
 */
public record VisaGuidanceResponse(String state, JsonNode content, List<AiCitation> citations, String languageCode,
                                   String model, Instant generatedAt, boolean isRefreshing, boolean isAiGenerated) {
}
