package com.triphuddle.api.destination.content;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.api.destination.config.ContentProperties;
import com.triphuddle.api.destination.dto.ContentSectionResponse;
import com.triphuddle.api.destination.entity.ContentSection;
import com.triphuddle.api.destination.entity.ContentStatus;
import com.triphuddle.api.destination.entity.GenerationPriority;
import com.triphuddle.api.destination.entity.Place;
import com.triphuddle.api.destination.entity.PlaceContent;
import com.triphuddle.api.destination.repository.PlaceContentRepository;
import com.triphuddle.api.destination.service.PlaceQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Serves a place's AI content: what is ready right away, an aged version while a new one is written, and the
 * English version while another language is written. A read starts what is missing or aged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ContentService {

    static final String DEFAULT_LANGUAGE = "en";
    // A read marks a section read at most this often, so busy places don't write on every read.
    private static final Duration VIEW_MARK_EVERY = Duration.ofHours(1);

    private final PlaceContentRepository contentRepository;
    private final PlaceQueryService placeQueryService;
    private final ContentGenerator generator;
    private final ContentProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Get a place's sections in a language, starting the generation of any that are missing or aged.
     *
     * @param placeId the ID of the place
     * @param languageCode the language asked for, ISO 639-1; anything else means English
     * @param sections the sections, or empty for all
     * @param userId the reader, whose daily share pays for what is missing
     * @param isStaff whether the reader may see hidden places
     * @return the sections, in the order asked
     */
    public List<ContentSectionResponse> read(UUID placeId, String languageCode, Collection<ContentSection> sections,
                                             UUID userId, boolean isStaff) {
        Place place = placeQueryService.visiblePlace(placeId, isStaff);
        String language = normalise(languageCode);
        List<ContentSection> wanted = sections == null || sections.isEmpty() ? List.of(ContentSection.values())
                : List.copyOf(Set.copyOf(sections));
        Instant now = clock.instant();
        List<Place> ancestors = null;
        List<ContentSectionResponse> responses = new ArrayList<>();
        List<UUID> read = new ArrayList<>();
        for (ContentSection section : wanted) {
            PlaceContent content = lockOrCreate(place, section, language);
            read.add(content.getId());
            if (needsGeneration(content, now)) {
                ancestors = ancestors == null ? placeQueryService.ancestorsOf(place) : ancestors;
                // Missing content holds the reader up; aged content is still served, so it waits on the budget.
                GenerationPriority priority = content.hasContent() ? GenerationPriority.REFRESH
                        : GenerationPriority.VIEW;
                ContentGenerator.Refusal refusal = generator.request(content, ancestors, priority, userId);
                if (refusal != ContentGenerator.Refusal.NONE && !content.hasContent()) {
                    responses.add(fallbackOr(place, section, language,
                            empty(section, "UNAVAILABLE", language)));
                    continue;
                }
            }
            if (content.hasContent()) {
                responses.add(toResponse(content, false));
            } else if (content.getStatus() == ContentStatus.FAILED && content.getPendingJobId() == null) {
                responses.add(fallbackOr(place, section, language, empty(section, "FAILED", language)));
            } else {
                responses.add(fallbackOr(place, section, language, empty(section, "GENERATING", language)));
            }
        }
        contentRepository.markViewed(read, now, now.minus(VIEW_MARK_EVERY));
        return responses;
    }

    /**
     * Have staff regenerate sections now, however fresh, unless one is already on its way.
     *
     * @param placeId the ID of the place
     * @param languageCode the language
     * @param sections the sections, or empty for all
     * @return the sections as they stand, being regenerated
     */
    public List<ContentSectionResponse> regenerate(UUID placeId, String languageCode,
                                                   Collection<ContentSection> sections) {
        Place place = placeQueryService.visiblePlace(placeId, true);
        String language = normalise(languageCode);
        List<Place> ancestors = placeQueryService.ancestorsOf(place);
        List<ContentSectionResponse> responses = new ArrayList<>();
        for (ContentSection section : sections == null || sections.isEmpty() ? List.of(ContentSection.values())
                : List.copyOf(Set.copyOf(sections))) {
            PlaceContent content = lockOrCreate(place, section, language);
            ContentGenerator.Refusal refusal = generator.request(content, ancestors, GenerationPriority.STAFF, null);
            responses.add(content.hasContent() ? toResponse(content, false)
                    : empty(section, refusal == ContentGenerator.Refusal.NONE ? "GENERATING" : "UNAVAILABLE",
                    language));
        }
        return responses;
    }

    // Missing and not on its way, failed long enough ago to try again, or aged out.
    private boolean needsGeneration(PlaceContent content, Instant now) {
        if (content.getPendingJobId() != null) {
            return false;
        }
        if (content.hasContent()) {
            return content.getExpiresAt() == null || !content.getExpiresAt().isAfter(now);
        }
        if (content.getStatus() == ContentStatus.FAILED) {
            Instant retryAt = content.getUpdatedAt().plus(Duration.ofMinutes(properties.retryFailedAfterMinutes()));
            return !retryAt.isAfter(now);
        }
        return true;
    }

    // While another language is written, a ready English version stands in.
    private ContentSectionResponse fallbackOr(Place place, ContentSection section, String language,
                                              ContentSectionResponse otherwise) {
        if (DEFAULT_LANGUAGE.equals(language)) {
            return otherwise;
        }
        return contentRepository.findForPlace(place.getId(), List.of(DEFAULT_LANGUAGE)).stream()
                .filter(content -> content.getSection() == section && content.hasContent())
                .findFirst()
                .map(content -> toResponse(content, true))
                .orElse(otherwise);
    }

    private PlaceContent lockOrCreate(Place place, ContentSection section, String language) {
        contentRepository.insertIfAbsent(place.getId(), section.name(), language);
        return contentRepository.lock(place.getId(), section, language).orElseThrow();
    }

    ContentSectionResponse toResponse(PlaceContent content, boolean isFallback) {
        return new ContentSectionResponse(content.getSection(), "READY", content.getLanguageCode(), isFallback,
                content.getPendingJobId() != null, parse(content.getContent()), List.copyOf(content.getCitations()),
                content.getModel(), content.getGeneratedAt(), true);
    }

    private static ContentSectionResponse empty(ContentSection section, String state, String language) {
        return new ContentSectionResponse(section, state, language, false, false, null, List.of(), null, null, true);
    }

    private JsonNode parse(String json) {
        try {
            return json == null ? null : objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            log.error("Stored content is not JSON", e);
            return null;
        }
    }

    /**
     * The ISO 639-1 code a language parameter or Accept-Language header asks for, else English.
     *
     * @param languageCode a code, a tag such as "pt-PT", a header, or null
     * @return the code
     */
    public static String normalise(String languageCode) {
        if (languageCode == null) {
            return DEFAULT_LANGUAGE;
        }
        String code = languageCode.strip().toLowerCase(Locale.ROOT);
        // An Accept-Language like "pt-PT,pt;q=0.9" starts with the one wanted.
        code = code.split("[,;\\-_]")[0];
        return code.matches("^[a-z]{2}$") ? code : DEFAULT_LANGUAGE;
    }
}
