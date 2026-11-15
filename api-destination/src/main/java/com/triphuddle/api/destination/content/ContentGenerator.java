package com.triphuddle.api.destination.content;

import com.triphuddle.api.destination.service.AfterCommit;
import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.ai.AiTextCompletedContract;
import com.triphuddle.amqp.ai.AiTextRequestedContract;
import com.triphuddle.amqp.ai.AiTextRequestedEvent;
import com.triphuddle.api.destination.config.ContentProperties;
import com.triphuddle.api.destination.entity.ContentStatus;
import com.triphuddle.api.destination.entity.GenerationPriority;
import com.triphuddle.api.destination.entity.Place;
import com.triphuddle.api.destination.entity.PlaceContent;
import com.triphuddle.api.destination.repository.AiUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Sends a section to the openai worker, within the day's token budget and the reader's daily limit, and marks it
 * pending until the answer comes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentGenerator {

    private final SectionSpecs specs;
    private final AiUsageRepository usageRepository;
    private final RabbitMQMessageProducer messageProducer;
    private final ContentProperties properties;
    private final Clock clock;

    /**
     * Why a section was not sent.
     */
    public enum Refusal {
        /** Sent, or already on its way. */
        NONE,
        /** The day's token budget for this kind of request is spent. */
        BUDGET,
        /** The reader started their day's share of generations. */
        USER_LIMIT
    }

    /**
     * Send a locked section unless it is already on its way, or the budget or the user's limit says no.
     *
     * @param content the locked section
     * @param ancestors the place's ancestors, for the prompt
     * @param priority why it is generated
     * @param userId the reader who started it, counted against their limit, or null
     * @return why it was not sent, or NONE
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Refusal request(PlaceContent content, List<Place> ancestors, GenerationPriority priority, UUID userId) {
        if (content.getPendingJobId() != null) {
            return Refusal.NONE;
        }
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        long spent = usageRepository.tokensOn(today);
        long allowed = priority == GenerationPriority.VIEW ? properties.dailyTokenHardLimit()
                : properties.dailyTokenBudget();
        if (spent >= allowed) {
            log.warn("AI budget spent, section not generated [contentId={}, priority={}, tokensToday={}]",
                    content.getId(), priority, spent);
            return Refusal.BUDGET;
        }
        if (priority == GenerationPriority.VIEW && userId != null
                && usageRepository.countGeneration(userId, today) > properties.maxGenerationsPerUserPerDay()) {
            log.info("User's daily generations used up [userId={}, contentId={}]", userId, content.getId());
            return Refusal.USER_LIMIT;
        }

        UUID jobId = UUID.randomUUID();
        content.setPendingJobId(jobId);
        content.setPendingPriority(priority);
        content.setRequestedAt(clock.instant());
        if (!content.hasContent()) {
            content.setStatus(ContentStatus.PENDING);
        }
        SectionSpec spec = specs.of(content.getSection());
        AiTextRequestedEvent event = new AiTextRequestedEvent(jobId,
                AiTextCompletedContract.AI_TEXT_COMPLETED_DESTINATION_QUEUE_NAME, spec.task(), null,
                PlacePrompts.system(content.getLanguageCode()),
                PlacePrompts.user(content.getPlace(), ancestors, spec), spec.schemaName(), spec.jsonSchema(),
                spec.isWebSearch(), null);
        AfterCommit.run(() -> messageProducer.publish(event, "", AiTextRequestedContract.AI_TEXT_REQUESTED_QUEUE_NAME));
        log.info("Section sent for generation [contentId={}, section={}, language={}, priority={}, jobId={}]",
                content.getId(), content.getSection(), content.getLanguageCode(), priority, jobId);
        return Refusal.NONE;
    }

    // The job goes once the section is marked pending for good; a rollback sends nothing.
}
