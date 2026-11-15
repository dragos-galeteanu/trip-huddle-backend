package com.triphuddle.api.destination.semantic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.ai.AiEmbeddingCompletedContract;
import com.triphuddle.amqp.ai.AiEmbeddingCompletedEvent;
import com.triphuddle.amqp.ai.AiEmbeddingRequestedContract;
import com.triphuddle.amqp.ai.AiEmbeddingRequestedEvent;
import com.triphuddle.amqp.ai.AiJobStatus;
import com.triphuddle.api.destination.config.ContentProperties;
import com.triphuddle.api.destination.config.SearchProperties;
import com.triphuddle.api.destination.dto.PlaceSummaryResponse;
import com.triphuddle.api.destination.dto.SemanticHitResponse;
import com.triphuddle.api.destination.dto.SemanticSearchResponse;
import com.triphuddle.api.destination.entity.Category;
import com.triphuddle.api.destination.entity.ContentSection;
import com.triphuddle.api.destination.entity.Place;
import com.triphuddle.api.destination.entity.PlaceKind;
import com.triphuddle.api.destination.entity.PlaceName;
import com.triphuddle.api.destination.repository.AiUsageRepository;
import com.triphuddle.api.destination.repository.EmbeddingRepository;
import com.triphuddle.api.destination.repository.EmbeddingRepository.Hit;
import com.triphuddle.api.destination.repository.EmbeddingRepository.PendingPlace;
import com.triphuddle.api.destination.repository.EmbeddingRepository.QueryState;
import com.triphuddle.api.destination.repository.PlaceContentRepository;
import com.triphuddle.api.destination.repository.PlaceRepository;
import com.triphuddle.api.destination.service.AfterCommit;
import com.triphuddle.api.destination.service.PlaceQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Finds places by meaning: each listed place is embedded from its facts and English overview, in the background,
 * and a query is embedded once and cached, so asking again is instant.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class SemanticSearchService {

    static final String PLACE_TASK = "place-embedding";
    static final String QUERY_TASK = "query-embedding";

    private static final int MAX_QUERY_LENGTH = 300;
    private static final Duration QUERY_RETRY_AFTER = Duration.ofMinutes(5);

    private final EmbeddingRepository embeddingRepository;
    private final PlaceRepository placeRepository;
    private final PlaceContentRepository contentRepository;
    private final PlaceQueryService placeQueryService;
    private final AiUsageRepository usageRepository;
    private final RabbitMQMessageProducer messageProducer;
    private final SearchProperties properties;
    private final ContentProperties contentProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Find places by what a query means, e.g. "quiet beach town with seafood".
     *
     * @param query the query
     * @param countryCode only in this country, or null
     * @param kind only of this kind, or null
     * @param withinId only within this place, or null
     * @param limit the most results
     * @return the places, or GENERATING while the query is embedded
     */
    public SemanticSearchResponse search(String query, String countryCode, PlaceKind kind, UUID withinId,
                                         int limit) {
        String text = normalise(query);
        if (text.isEmpty() || text.length() > MAX_QUERY_LENGTH) {
            throw new IllegalStateException("Describe what you look for in 1 to %d characters"
                    .formatted(MAX_QUERY_LENGTH));
        }
        String withinPath = withinId == null ? null : placeQueryService.visiblePlace(withinId, false).getPath();
        String hash = sha256(text);
        Instant now = clock.instant();
        QueryState state = embeddingRepository.lockQuery(hash, text, now);
        if (state.hasEmbedding()) {
            List<Hit> hits = embeddingRepository.searchByQuery(hash,
                    countryCode == null ? null : countryCode.toUpperCase(Locale.ROOT),
                    kind == null ? null : kind.name(), withinPath, clamp(limit));
            return new SemanticSearchResponse("READY", toResults(hits));
        }
        boolean isWaiting = state.pendingJobId() != null && state.requestedAt() != null
                && state.requestedAt().isAfter(now.minusSeconds(properties.queryPendingTimeoutSeconds()));
        if (isWaiting) {
            return new SemanticSearchResponse("GENERATING", List.of());
        }
        if (state.failedAt() != null && state.failedAt().isAfter(now.minus(QUERY_RETRY_AFTER))) {
            return new SemanticSearchResponse("FAILED", List.of());
        }
        if (isOverHardLimit()) {
            return new SemanticSearchResponse("UNAVAILABLE", List.of());
        }
        UUID jobId = UUID.randomUUID();
        embeddingRepository.markQueryPending(hash, jobId, now);
        send(jobId, QUERY_TASK, List.of(text));
        return new SemanticSearchResponse("GENERATING", List.of());
    }

    /**
     * Find the places most like one.
     *
     * @param placeId the ID of the place
     * @param isSameCountry whether to stay in its country
     * @param limit the most results
     * @return the places, or GENERATING until the place is embedded
     */
    @Transactional(readOnly = true)
    public SemanticSearchResponse similar(UUID placeId, boolean isSameCountry, int limit) {
        Place place = placeQueryService.visiblePlace(placeId, false);
        if (!embeddingRepository.hasEmbedding(place.getId())) {
            return new SemanticSearchResponse("GENERATING", List.of());
        }
        return new SemanticSearchResponse("READY", toResults(embeddingRepository.searchByPlace(place.getId(),
                isSameCountry ? place.getCountryCode() : null, clamp(limit))));
    }

    /**
     * Send the next batch of places whose embedding is missing or stale, within the day's bulk budget.
     *
     * @return how many places were sent
     */
    public int embedDue() {
        if (usageRepository.tokensOn(LocalDate.now(clock.withZone(ZoneOffset.UTC)))
                >= contentProperties.dailyTokenBudget()) {
            return 0;
        }
        List<UUID> due = embeddingRepository.findDue(Math.min(properties.embeddingBatchSize(),
                AiEmbeddingRequestedEvent.MAX_INPUTS));
        if (due.isEmpty()) {
            return 0;
        }
        Map<UUID, Place> places = placeRepository.findAllWithParent(due).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        Map<UUID, String> hashes = embeddingRepository.hashesOf(due);
        List<UUID> unchanged = new ArrayList<>();
        List<PendingPlace> pending = new ArrayList<>();
        List<String> inputs = new ArrayList<>();
        Instant now = clock.instant();
        for (UUID id : due) {
            Place place = places.get(id);
            if (place == null) {
                continue;
            }
            String text = textOf(place);
            String hash = sha256(properties.embeddingModel() + "|" + properties.embeddingDimensions() + "|" + text);
            if (hash.equals(hashes.get(id))) {
                unchanged.add(id);
            } else {
                pending.add(new PendingPlace(id, inputs.size(), hash));
                inputs.add(text);
            }
        }
        embeddingRepository.markChecked(unchanged, now);
        if (!pending.isEmpty()) {
            UUID jobId = UUID.randomUUID();
            embeddingRepository.markPending(jobId, pending, now);
            send(jobId, PLACE_TASK, inputs);
            log.info("Place embeddings requested [jobId={}, places={}, unchanged={}]", jobId, pending.size(),
                    unchanged.size());
        }
        return pending.size();
    }

    /**
     * Store the worker's embeddings for places or a query; an answer no longer waited for only counts its
     * tokens.
     *
     * @param reply the answer
     */
    public void onEmbeddings(AiEmbeddingCompletedEvent reply) {
        Instant now = clock.instant();
        usageRepository.addTokens(LocalDate.ofInstant(now, ZoneOffset.UTC), reply.inputTokens(), 0);
        boolean isCompleted = reply.status() == AiJobStatus.COMPLETED && reply.vectors() != null;
        if (QUERY_TASK.equals(reply.task())) {
            String vector = isCompleted && reply.vectors().size() == 1 ? vector(reply.vectors().get(0)) : null;
            embeddingRepository.storeQuery(reply.jobId(), vector, reply.model(), now);
            return;
        }
        List<PendingPlace> pending = embeddingRepository.findPending(reply.jobId());
        if (pending.isEmpty()) {
            log.info("Embeddings no longer waited for [jobId={}]", reply.jobId());
            return;
        }
        if (!isCompleted) {
            log.warn("Place embeddings failed [jobId={}, error={}]", reply.jobId(), reply.error());
            embeddingRepository.releasePending(reply.jobId());
            return;
        }
        int stored = 0;
        for (PendingPlace place : pending) {
            String vector = place.position() < reply.vectors().size()
                    ? vector(reply.vectors().get(place.position())) : null;
            if (vector != null) {
                embeddingRepository.storePlace(place.placeId(), vector, place.hash(), reply.model(), now);
                stored++;
            }
        }
        // Any left, e.g. with a vector of the wrong length, go back to wait for the next run.
        embeddingRepository.releasePending(reply.jobId());
        log.info("Place embeddings stored [jobId={}, stored={}, of={}]", reply.jobId(), stored, pending.size());
    }

    /**
     * Release places from embedding jobs whose answer is overdue.
     *
     * @return how many were released
     */
    public int releaseOverdue() {
        return embeddingRepository.releaseOverdue(clock.instant().minus(
                Duration.ofMinutes(properties.embeddingPendingTimeoutMinutes())));
    }

    // What a place is about, in English, which the embedding model reads best.
    String textOf(Place place) {
        StringBuilder text = new StringBuilder(place.getName()).append(" (")
                .append(place.getKind().name().toLowerCase(Locale.ROOT)).append(")");
        List<Place> ancestors = placeQueryService.ancestorsOf(place);
        if (!ancestors.isEmpty()) {
            text.append(", in ").append(ancestors.stream().map(Place::getName).collect(Collectors.joining(" > ")));
        }
        text.append(". Country: ").append(place.getCountryCode()).append('.');
        List<String> otherNames = place.getNames().stream().map(PlaceName::getName)
                .filter(name -> !name.equals(place.getName())).distinct().sorted().limit(10).toList();
        if (!otherNames.isEmpty()) {
            text.append(" Also called: ").append(String.join(", ", otherNames)).append('.');
        }
        if (!place.getCategories().isEmpty()) {
            text.append(" Categories: ").append(place.getCategories().stream().map(Category::getName).sorted()
                    .collect(Collectors.joining(", "))).append('.');
        }
        if (!place.getTags().isEmpty()) {
            text.append(" Tags: ").append(place.getTags().stream().sorted().collect(Collectors.joining(", ")))
                    .append('.');
        }
        contentRepository.findForPlace(place.getId(), List.of("en")).stream()
                .filter(content -> content.getSection() == ContentSection.OVERVIEW && content.getContent() != null)
                .findFirst().ifPresent(content -> appendOverview(text, content.getContent()));
        return text.toString();
    }

    private void appendOverview(StringBuilder text, String content) {
        try {
            JsonNode overview = objectMapper.readTree(content);
            text.append(' ').append(overview.path("summary").asText(""));
            List<String> more = new ArrayList<>();
            for (String field : List.of("highlights", "goodFor")) {
                overview.path(field).forEach(item -> more.add(item.asText()));
            }
            if (!more.isEmpty()) {
                text.append(" Known for: ").append(String.join("; ", more)).append('.');
            }
        } catch (Exception e) {
            log.warn("Overview not readable for embedding", e);
        }
    }

    private List<SemanticHitResponse> toResults(List<Hit> hits) {
        Map<UUID, PlaceSummaryResponse> summaries = placeQueryService.summariesOf(
                        hits.stream().map(Hit::placeId).toList()).stream()
                .collect(Collectors.toMap(PlaceSummaryResponse::id, Function.identity()));
        return hits.stream().filter(hit -> summaries.containsKey(hit.placeId()))
                .map(hit -> new SemanticHitResponse(summaries.get(hit.placeId()),
                        Math.round(hit.similarity() * 1000) / 1000.0))
                .toList();
    }

    private void send(UUID jobId, String task, List<String> inputs) {
        AiEmbeddingRequestedEvent event = new AiEmbeddingRequestedEvent(jobId,
                AiEmbeddingCompletedContract.AI_EMBEDDING_COMPLETED_DESTINATION_QUEUE_NAME, task,
                properties.embeddingModel() == null || properties.embeddingModel().isBlank() ? null
                        : properties.embeddingModel(), inputs);
        AfterCommit.run(() -> messageProducer.publish(event, "",
                AiEmbeddingRequestedContract.AI_EMBEDDING_REQUESTED_QUEUE_NAME));
    }

    private boolean isOverHardLimit() {
        return usageRepository.tokensOn(LocalDate.now(clock.withZone(ZoneOffset.UTC)))
                >= contentProperties.dailyTokenHardLimit();
    }

    private int clamp(int limit) {
        return Math.max(1, Math.min(limit, properties.maxResults()));
    }

    // pgvector's text form, or null if the vector isn't the column's length.
    private String vector(float[] values) {
        if (values == null || values.length != properties.embeddingDimensions()) {
            return null;
        }
        StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            text.append(i == 0 ? "" : ",").append(values[i]);
        }
        return text.append(']').toString();
    }

    static String normalise(String query) {
        return query == null ? "" : query.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
