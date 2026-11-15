package com.triphuddle.api.destination.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.amqp.ai.AiCitation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A question about a place and its answer.
 *
 * @param id the ID of the question
 * @param placeId the place
 * @param question what was asked
 * @param languageCode the language it is answered in
 * @param state GENERATING (ask again shortly), READY or FAILED
 * @param answer {@code answer}, {@code tips} and {@code caveats}, or null
 * @param citations the pages the model read
 * @param model the model that answered
 * @param error why it failed
 * @param requestedAt when it was asked
 * @param answeredAt when it was answered
 * @param isAiGenerated always true, for the label the frontend shows
 */
public record QuestionResponse(UUID id, UUID placeId, String question, String languageCode, String state,
                               JsonNode answer, List<AiCitation> citations, String model, String error,
                               Instant requestedAt, Instant answeredAt, boolean isAiGenerated) {
}
