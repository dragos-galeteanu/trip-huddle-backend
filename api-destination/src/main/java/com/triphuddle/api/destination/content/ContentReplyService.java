package com.triphuddle.api.destination.content;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.amqp.ai.AiJobStatus;
import com.triphuddle.amqp.ai.AiTextCompletedEvent;
import com.triphuddle.api.destination.config.ContentProperties;
import com.triphuddle.api.destination.entity.ContentStatus;
import com.triphuddle.api.destination.entity.PlaceContent;
import com.triphuddle.api.destination.repository.AiUsageRepository;
import com.triphuddle.api.destination.repository.PlaceContentRepository;
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
import java.util.Optional;

/**
 * Takes the openai worker's answers: a good one becomes the section's new version, a failed one leaves the last
 * version in place and is tried again later. Every answer's tokens count against the day.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ContentReplyService {

    private final PlaceContentRepository contentRepository;
    private final AiUsageRepository usageRepository;
    private final SectionSpecs specs;
    private final ContentProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Apply an answer to the section that waits for it; one for a job no longer waited for only counts its tokens.
     *
     * @param reply the answer
     */
    public void onCompleted(AiTextCompletedEvent reply) {
        Instant now = clock.instant();
        usageRepository.addTokens(LocalDate.ofInstant(now, ZoneOffset.UTC), reply.inputTokens(),
                reply.outputTokens());
        Optional<PlaceContent> found = contentRepository.lockByPendingJobId(reply.jobId());
        if (found.isEmpty()) {
            log.info("Answer for a job no longer waited for [jobId={}]", reply.jobId());
            return;
        }
        PlaceContent content = found.get();
        content.setPendingJobId(null);
        content.setPendingPriority(null);

        String error = reply.status() == AiJobStatus.COMPLETED ? check(reply.output()) : reply.error();
        if (error != null) {
            content.setFailedAttempts(content.getFailedAttempts() + 1);
            content.setLastError(error);
            if (content.hasContent()) {
                // The last version stays; its refresh is tried again later rather than at once.
                content.setExpiresAt(now.plus(Duration.ofMinutes(properties.retryFailedAfterMinutes())));
                content.setStatus(ContentStatus.READY);
            } else {
                content.setStatus(ContentStatus.FAILED);
            }
            log.warn("Section generation failed [contentId={}, section={}, language={}, error={}]", content.getId(),
                    content.getSection(), content.getLanguageCode(), error);
            return;
        }
        content.setContent(reply.output());
        content.setCitations(reply.citations() == null ? new ArrayList<>() : new ArrayList<>(reply.citations()));
        content.setModel(reply.model());
        content.setGeneratedAt(now);
        content.setExpiresAt(now.plus(specs.of(content.getSection()).freshFor()));
        content.setStatus(ContentStatus.READY);
        content.setFailedAttempts(0);
        content.setLastError(null);
        log.info("Section generated [contentId={}, section={}, language={}, model={}, tokens={}]", content.getId(),
                content.getSection(), content.getLanguageCode(), reply.model(),
                reply.inputTokens() + reply.outputTokens());
    }

    // The worker asked for the schema in strict mode; this still refuses an answer that isn't an object.
    private String check(String output) {
        try {
            JsonNode node = output == null ? null : objectMapper.readTree(output);
            if (node == null || !node.isObject() || !node.fields().hasNext()) {
                return "The answer is not a JSON object";
            }
            return null;
        } catch (JsonProcessingException e) {
            return "The answer is not JSON: " + e.getOriginalMessage();
        }
    }
}
