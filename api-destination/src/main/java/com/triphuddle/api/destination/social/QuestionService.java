package com.triphuddle.api.destination.social;

import com.triphuddle.common.filter.CursorPaginationFilter;
import com.triphuddle.common.dto.PageCursor;
import com.triphuddle.common.dto.CursorPaginated;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.ai.AiJobStatus;
import com.triphuddle.amqp.ai.AiTextCompletedContract;
import com.triphuddle.amqp.ai.AiTextCompletedEvent;
import com.triphuddle.amqp.ai.AiTextRequestedContract;
import com.triphuddle.amqp.ai.AiTextRequestedEvent;
import com.triphuddle.api.destination.config.ContentProperties;
import com.triphuddle.api.destination.config.SocialProperties;
import com.triphuddle.api.destination.content.ContentService;
import com.triphuddle.api.destination.content.PlacePrompts;
import com.triphuddle.api.destination.dto.QuestionResponse;
import com.triphuddle.api.destination.entity.ContentStatus;
import com.triphuddle.api.destination.entity.Place;
import com.triphuddle.api.destination.entity.PlaceQuestion;
import com.triphuddle.api.destination.repository.AiUsageRepository;
import com.triphuddle.api.destination.repository.PlaceQuestionRepository;
import com.triphuddle.api.destination.service.AfterCommit;
import com.triphuddle.api.destination.service.PlaceQueryService;
import com.triphuddle.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Answers a traveller's own question about a place through the openai worker, with web search; each answer is
 * theirs alone.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class QuestionService {

    static final String TASK = "place-question";

    private static final String SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["answer","tips","caveats"],
             "properties":{"answer":{"type":"string"},"tips":{"type":"array","items":{"type":"string"}},
                           "caveats":{"type":"array","items":{"type":"string"}}}}""";

    private final PlaceQuestionRepository repository;
    private final PlaceQueryService placeQueryService;
    private final AiUsageRepository usageRepository;
    private final RabbitMQMessageProducer messageProducer;
    private final SocialProperties properties;
    private final ContentProperties contentProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Ask about a place; follow the question until READY.
     *
     * @param placeId the ID of the place
     * @param userId who asks
     * @param question the question
     * @param language the language, or null for English
     * @return the question, GENERATING
     */
    public QuestionResponse ask(UUID placeId, UUID userId, String question, String language) {
        String text = question.strip();
        if (text.length() > properties.maxQuestionLength()) {
            throw new IllegalStateException("Keep the question under %d characters"
                    .formatted(properties.maxQuestionLength()));
        }
        Place place = placeQueryService.visiblePlace(placeId, false);
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (usageRepository.tokensOn(today) >= contentProperties.dailyTokenHardLimit()) {
            throw new IllegalStateException("Questions are paused for today; try again tomorrow");
        }
        if (usageRepository.countGeneration(userId, today) > contentProperties.maxGenerationsPerUserPerDay()) {
            throw new IllegalStateException("You've used today's AI requests; try again tomorrow");
        }

        PlaceQuestion asked = new PlaceQuestion();
        asked.setPlaceId(place.getId());
        asked.setUserId(userId);
        asked.setQuestion(text);
        asked.setLanguageCode(ContentService.normalise(language));
        asked.setStatus(ContentStatus.PENDING);
        asked.setPendingJobId(UUID.randomUUID());
        asked.setRequestedAt(clock.instant());
        PlaceQuestion saved = repository.save(asked);

        String prompt = PlacePrompts.facts(place, placeQueryService.ancestorsOf(place)) + "\nQuestion: " + text;
        AiTextRequestedEvent event = new AiTextRequestedEvent(saved.getPendingJobId(),
                AiTextCompletedContract.AI_TEXT_COMPLETED_DESTINATION_QUEUE_NAME, TASK, null,
                system(saved.getLanguageCode()), prompt, "place_answer", SCHEMA, true, 1500);
        AfterCommit.run(() -> messageProducer.publish(event, "", AiTextRequestedContract.AI_TEXT_REQUESTED_QUEUE_NAME));
        log.info("Place question asked [id={}, placeId={}, userId={}]", saved.getId(), place.getId(), userId);
        return toResponse(saved);
    }

    /**
     * Get one of the caller's questions.
     *
     * @param questionId the ID of the question
     * @param userId the caller
     * @param isStaff whether the caller is staff, who see any
     * @return the question
     */
    @Transactional(readOnly = true)
    public QuestionResponse get(UUID questionId, UUID userId, boolean isStaff) {
        return repository.findByIdAndDeletedAtIsNull(questionId)
                .filter(question -> isStaff || question.getUserId().equals(userId))
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Question with id=%s not found"
                        .formatted(questionId)));
    }

    /**
     * Get the caller's questions about a place, the newest first.
     *
     * @param placeId the ID of the place
     * @param userId the caller
     * @param filter the cursor and page size
     * @return a page of questions
     */
    @Transactional(readOnly = true)
    public CursorPaginated<QuestionResponse> getMine(UUID placeId, UUID userId, CursorPaginationFilter filter) {
        PageCursor cursor = PageCursor.decode(filter.getCursor(), 2);
        boolean isFirst = cursor == null;
        List<PlaceQuestion> found = repository.findMine(placeId, userId, isFirst,
                isFirst ? PageCursor.FIRST_INSTANT : cursor.instantAt(0),
                isFirst ? PageCursor.FIRST_ID : cursor.uuidAt(1), filter.toLimit());
        return CursorPaginated.of(found, filter.getLimit(),
                question -> PageCursor.of(question.getRequestedAt(), question.getId()),
                questions -> questions.stream().map(this::toResponse).toList());
    }

    /**
     * Apply the worker's answer; one no longer waited for only counts its tokens.
     *
     * @param reply the answer
     */
    public void onAnswer(AiTextCompletedEvent reply) {
        Instant now = clock.instant();
        usageRepository.addTokens(LocalDate.ofInstant(now, ZoneOffset.UTC), reply.inputTokens(),
                reply.outputTokens());
        repository.lockByPendingJobId(reply.jobId()).ifPresentOrElse(question -> {
            question.setPendingJobId(null);
            question.setModel(reply.model());
            JsonNode answer = reply.status() == AiJobStatus.COMPLETED ? json(reply.output()) : null;
            if (answer == null || !answer.path("answer").isTextual()) {
                question.setStatus(ContentStatus.FAILED);
                question.setError(reply.status() == AiJobStatus.COMPLETED ? "The answer was not readable"
                        : reply.error());
                return;
            }
            question.setAnswer(reply.output());
            question.setCitations(new ArrayList<>(reply.citations() == null ? List.of() : reply.citations()));
            question.setStatus(ContentStatus.READY);
            question.setAnsweredAt(now);
        }, () -> log.info("Answer for a question no longer waited for [jobId={}]", reply.jobId()));
    }

    /**
     * Give up on questions whose answer is overdue.
     *
     * @return how many were given up on
     */
    public int giveUpOverdue() {
        return repository.giveUpOverdue(clock.instant().minus(
                Duration.ofMinutes(properties.questionPendingTimeoutMinutes())));
    }

    /**
     * Delete every question of a deleted user, emptying them.
     *
     * @param userId the ID of the user
     * @return how many were deleted
     */
    public int deleteForUser(UUID userId) {
        return repository.deleteByUserId(userId, clock.instant());
    }

    private static String system(String languageCode) {
        return """
                You answer a traveller's question about one place, for a trip-planning app, in %s.
                Use the place's facts to be sure which place it is, and check current details on the web.
                Answer only about travelling to or around this place; if the question is about something else,
                say briefly that you can only help with the trip. Do not invent hours, prices or names; when
                unsure, say so in caveats. Do not mention that you are an AI. Answer only with JSON matching the
                schema.
                """.formatted(PlacePrompts.languageName(languageCode));
    }

    private QuestionResponse toResponse(PlaceQuestion question) {
        String state = switch (question.getStatus()) {
            case PENDING -> "GENERATING";
            case READY -> "READY";
            case FAILED -> "FAILED";
        };
        return new QuestionResponse(question.getId(), question.getPlaceId(), question.getQuestion(),
                question.getLanguageCode(), state, json(question.getAnswer()), question.getCitations(),
                question.getModel(), question.getError(), question.getRequestedAt(), question.getAnsweredAt(), true);
    }

    private JsonNode json(String content) {
        if (content == null) {
            return null;
        }
        try {
            return objectMapper.readTree(content);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
