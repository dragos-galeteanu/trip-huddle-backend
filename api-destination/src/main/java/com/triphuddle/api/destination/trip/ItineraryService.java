package com.triphuddle.api.destination.trip;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.ai.AiJobStatus;
import com.triphuddle.amqp.ai.AiTextCompletedContract;
import com.triphuddle.amqp.ai.AiTextCompletedEvent;
import com.triphuddle.amqp.ai.AiTextRequestedContract;
import com.triphuddle.amqp.ai.AiTextRequestedEvent;
import com.triphuddle.api.destination.config.ContentProperties;
import com.triphuddle.api.destination.config.TripProperties;
import com.triphuddle.api.destination.dto.ItineraryDayResponse;
import com.triphuddle.api.destination.dto.ItineraryItemRequest;
import com.triphuddle.api.destination.dto.ItineraryItemResponse;
import com.triphuddle.api.destination.dto.ItineraryResponse;
import com.triphuddle.api.destination.entity.Itinerary;
import com.triphuddle.api.destination.entity.ItineraryDay;
import com.triphuddle.api.destination.entity.ItineraryItem;
import com.triphuddle.api.destination.entity.ItineraryStatus;
import com.triphuddle.api.destination.entity.Place;
import com.triphuddle.api.destination.entity.TimeOfDay;
import com.triphuddle.api.destination.entity.TripPlan;
import com.triphuddle.api.destination.mapper.PlaceMapper;
import com.triphuddle.api.destination.repository.AiUsageRepository;
import com.triphuddle.api.destination.repository.ItineraryRepository;
import com.triphuddle.api.destination.repository.PlaceRepository;
import com.triphuddle.api.destination.repository.PlaceSearchHit;
import com.triphuddle.api.destination.repository.PlaceSearchQuery;
import com.triphuddle.api.destination.repository.PlaceSearchRepository;
import com.triphuddle.api.destination.repository.QuotaRepository;
import com.triphuddle.api.destination.service.AccessService;
import com.triphuddle.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Has the openai worker write itineraries for trip plans, or rewrite one day, within the owner's monthly quota and
 * the day's token limits; and lets travellers edit them afterwards.
 */
@Slf4j
@Service
@Transactional
public class ItineraryService {

    static final String ITINERARY_TASK = "itinerary";
    static final String DAY_TASK = "itinerary-day";

    private final ItineraryRepository itineraryRepository;
    private final TripPlanService planService;
    private final PlaceRepository placeRepository;
    private final PlaceSearchRepository placeSearchRepository;
    private final PlaceMapper placeMapper;
    private final AccessService accessService;
    private final QuotaRepository quotaRepository;
    private final AiUsageRepository usageRepository;
    private final RabbitMQMessageProducer messageProducer;
    private final TripNotifier notifier;
    private final TripProperties properties;
    private final ContentProperties contentProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ItineraryService(ItineraryRepository itineraryRepository, TripPlanService planService,
                            PlaceRepository placeRepository, PlaceSearchRepository placeSearchRepository,
                            PlaceMapper placeMapper, AccessService accessService, QuotaRepository quotaRepository,
                            AiUsageRepository usageRepository, RabbitMQMessageProducer messageProducer,
                            TripNotifier notifier, TripProperties properties, ContentProperties contentProperties,
                            ObjectMapper objectMapper, Clock clock) {
        this.itineraryRepository = itineraryRepository;
        this.planService = planService;
        this.placeRepository = placeRepository;
        this.placeSearchRepository = placeSearchRepository;
        this.placeMapper = placeMapper;
        this.accessService = accessService;
        this.quotaRepository = quotaRepository;
        this.usageRepository = usageRepository;
        this.messageProducer = messageProducer;
        this.notifier = notifier;
        this.properties = properties;
        this.contentProperties = contentProperties;
        // Unknown fields in an answer are dropped rather than failing it; the schema already pins the shape.
        this.objectMapper = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.clock = clock;
    }

    /**
     * Have an itinerary written for a plan, taking one unit of its owner's monthly quota.
     *
     * @param planId the ID of the plan
     * @param userId the traveller asking
     * @return the itinerary, GENERATING
     */
    public ItineraryResponse generate(UUID planId, UUID userId) {
        TripPlan plan = planService.find(planId);
        if (itineraryRepository.isGenerating(planId)) {
            throw new IllegalStateException("An itinerary is already being written for this plan");
        }
        requireWithinTokenLimit();
        requireUserShare(userId);
        String key = properties.itineraryQuotaEntitlement();
        int limit = accessService.quotaOf(plan.getOwnerType(), plan.getOwnerId(), key);
        if (!quotaRepository.tryTake(plan.getOwnerType(), plan.getOwnerId(), key, period(clock.instant()), limit)) {
            throw new IllegalStateException(limit == 0
                    ? "Your license doesn't include AI itineraries"
                    : "This month's %d itineraries are used up".formatted(limit));
        }

        Itinerary itinerary = new Itinerary();
        itinerary.setPlan(plan);
        itinerary.setStatus(ItineraryStatus.GENERATING);
        itinerary.setLanguageCode(plan.getLanguageCode());
        itinerary.setRequestedByUserId(userId);
        itinerary.setPendingJobId(UUID.randomUUID());
        itinerary.setRequestedAt(clock.instant());
        Itinerary saved = itineraryRepository.saveAndFlush(itinerary);

        List<Place> destinations = destinationsOf(plan);
        Collection<Place> candidates = candidatesFor(destinations, plan);
        send(saved.getPendingJobId(), ITINERARY_TASK, plan.getLanguageCode(),
                ItineraryPrompts.itinerary(plan, destinations, candidates, placesOf(plan.getMustSeePlaceIds())),
                "itinerary", ItinerarySchemas.ITINERARY);
        log.info("Itinerary requested [id={}, planId={}, userId={}, candidates={}]", saved.getId(), planId, userId,
                candidates.size());
        return toResponse(saved);
    }

    /**
     * Have one day of a ready itinerary written again, optionally as the travellers ask.
     *
     * @param itineraryId the ID of the itinerary
     * @param dayNumber the day
     * @param instructions what the travellers want, or null
     * @param userId the traveller asking
     * @return the itinerary, with the day being rewritten
     */
    public ItineraryResponse regenerateDay(UUID itineraryId, int dayNumber, String instructions, UUID userId) {
        Itinerary itinerary = lockReady(itineraryId);
        ItineraryDay day = dayOf(itinerary, dayNumber);
        if (day.getPendingJobId() != null) {
            throw new IllegalStateException("Day %d is already being rewritten".formatted(dayNumber));
        }
        requireWithinTokenLimit();
        requireUserShare(userId);
        day.setPendingJobId(UUID.randomUUID());
        day.setRequestedAt(clock.instant());
        TripPlan plan = itinerary.getPlan();
        List<Place> destinations = destinationsOf(plan);
        List<ItineraryDay> others = itinerary.getDays().stream().filter(other -> other != day).toList();
        send(day.getPendingJobId(), DAY_TASK, itinerary.getLanguageCode(), ItineraryPrompts.day(plan, destinations,
                candidatesFor(destinations, plan), day, others, instructions), "itinerary_day", ItinerarySchemas.DAY);
        log.info("Itinerary day requested [itineraryId={}, day={}, userId={}]", itineraryId, dayNumber, userId);
        return toResponse(itinerary);
    }

    /**
     * Apply the worker's answer to the itinerary or day waiting for it; an answer no longer waited for only
     * counts its tokens.
     *
     * @param reply the answer
     */
    public void onAnswer(AiTextCompletedEvent reply) {
        Instant now = clock.instant();
        usageRepository.addTokens(LocalDate.ofInstant(now, ZoneOffset.UTC), reply.inputTokens(),
                reply.outputTokens());
        if (DAY_TASK.equals(reply.task())) {
            itineraryRepository.lockByPendingDayJobId(reply.jobId()).ifPresent(itinerary -> applyDay(itinerary,
                    reply));
            return;
        }
        itineraryRepository.lockByPendingJobId(reply.jobId()).ifPresentOrElse(itinerary -> {
            itinerary.setPendingJobId(null);
            itinerary.setModel(reply.model());
            ItineraryAnswer answer = reply.status() == AiJobStatus.COMPLETED ? parse(reply.output(),
                    ItineraryAnswer.class) : null;
            if (answer == null || answer.days() == null || answer.days().isEmpty()) {
                fail(itinerary, reply.status() == AiJobStatus.COMPLETED ? "The answer was not an itinerary"
                        : reply.error());
                return;
            }
            TripPlan plan = itinerary.getPlan();
            Set<UUID> known = knownPlaceIds(answer.days().stream().flatMap(day -> day.items().stream()).toList());
            int dayNumber = 1;
            for (ItineraryAnswer.Day answered : answer.days()) {
                if (dayNumber > plan.dayCount()) {
                    break;
                }
                ItineraryDay day = new ItineraryDay();
                day.setItinerary(itinerary);
                day.setDayNumber(dayNumber);
                day.setDate(plan.getStartDate().plusDays(dayNumber - 1L));
                fillDay(day, answered, known);
                itinerary.getDays().add(day);
                dayNumber++;
            }
            itinerary.setSummary(answer.summary());
            itinerary.setGeneratedAt(now);
            itinerary.setStatus(ItineraryStatus.READY);
            notifier.itineraryFinished(itinerary);
            log.info("Itinerary written [id={}, days={}, model={}]", itinerary.getId(), itinerary.getDays().size(),
                    reply.model());
        }, () -> log.info("Answer for an itinerary no longer waited for [jobId={}]", reply.jobId()));
    }

    /**
     * Mark itineraries whose answer is overdue as failed, giving their quota back, and give up on overdue day
     * rewrites, leaving those days as they were.
     *
     * @return how many itineraries and days were given up on
     */
    public int failOverdue() {
        Instant cutoff = clock.instant().minus(Duration.ofMinutes(properties.pendingTimeoutMinutes()));
        int days = itineraryRepository.clearOverdueDays(cutoff);
        List<Itinerary> overdue = itineraryRepository.lockOverdue(cutoff, PageRequest.of(0, 50));
        overdue.forEach(itinerary -> {
            itinerary.setPendingJobId(null);
            fail(itinerary, "The itinerary took too long to write; try again");
        });
        return overdue.size() + days;
    }

    /**
     * Put a day's items in a new order.
     *
     * @param itineraryId the ID of the itinerary
     * @param dayNumber the day
     * @param itemIds every item of the day, in the new order
     * @return the itinerary
     */
    public ItineraryResponse reorder(UUID itineraryId, int dayNumber, List<UUID> itemIds) {
        ItineraryDay day = dayOf(lockReady(itineraryId), dayNumber);
        Map<UUID, ItineraryItem> byId = day.getItems().stream()
                .collect(Collectors.toMap(ItineraryItem::getId, Function.identity()));
        if (itemIds.size() != byId.size() || !byId.keySet().containsAll(itemIds)) {
            throw new IllegalStateException("Give every item of the day once, in the new order");
        }
        for (int position = 0; position < itemIds.size(); position++) {
            byId.get(itemIds.get(position)).setPosition(position);
        }
        day.getItems().sort((a, b) -> Integer.compare(a.getPosition(), b.getPosition()));
        return toResponse(day.getItinerary());
    }

    /**
     * Add something to a day, at its end.
     *
     * @param itineraryId the ID of the itinerary
     * @param dayNumber the day
     * @param request the item
     * @return the itinerary
     */
    public ItineraryResponse addItem(UUID itineraryId, int dayNumber, ItineraryItemRequest request) {
        ItineraryDay day = dayOf(lockReady(itineraryId), dayNumber);
        if (request.getPlaceId() != null && placeRepository.findByIdAndDeletedAtIsNull(request.getPlaceId())
                .filter(Place::isListed).isEmpty()) {
            throw new IllegalStateException("Place with id=%s is not a place travellers can go to"
                    .formatted(request.getPlaceId()));
        }
        ItineraryItem item = new ItineraryItem();
        item.setDay(day);
        item.setPosition(day.getItems().size());
        item.setTimeOfDay(request.getTimeOfDay());
        item.setTitle(request.getTitle().strip());
        item.setDescription(blankToNull(request.getDescription()));
        item.setPlaceId(request.getPlaceId());
        item.setDurationMinutes(request.getDurationMinutes());
        item.setTips(blankToNull(request.getTips()));
        day.getItems().add(item);
        return toResponse(day.getItinerary());
    }

    /**
     * Remove something from a day.
     *
     * @param itineraryId the ID of the itinerary
     * @param itemId the ID of the item
     * @return the itinerary
     */
    public ItineraryResponse removeItem(UUID itineraryId, UUID itemId) {
        Itinerary itinerary = lockReady(itineraryId);
        for (ItineraryDay day : itinerary.getDays()) {
            if (day.getItems().removeIf(item -> item.getId().equals(itemId))) {
                for (int position = 0; position < day.getItems().size(); position++) {
                    day.getItems().get(position).setPosition(position);
                }
                return toResponse(itinerary);
            }
        }
        throw new ResourceNotFoundException("Item with id=%s not found".formatted(itemId));
    }

    /**
     * Get an itinerary.
     *
     * @param itineraryId the ID of the itinerary
     * @return the itinerary
     */
    @Transactional(readOnly = true)
    public ItineraryResponse get(UUID itineraryId) {
        return toResponse(itineraryRepository.findByIdAndDeletedAtIsNull(itineraryId)
                .orElseThrow(() -> notFound(itineraryId)));
    }

    /**
     * Get a plan's itineraries, the newest first.
     *
     * @param planId the ID of the plan
     * @return the itineraries
     */
    @Transactional(readOnly = true)
    public List<ItineraryResponse> getForPlan(UUID planId) {
        return itineraryRepository.findForPlan(planId).stream().map(this::toResponse).toList();
    }

    private void applyDay(Itinerary itinerary, AiTextCompletedEvent reply) {
        ItineraryDay day = itinerary.getDays().stream().filter(found -> reply.jobId().equals(found.getPendingJobId()))
                .findFirst().orElseThrow();
        day.setPendingJobId(null);
        ItineraryAnswer.Day answer = reply.status() == AiJobStatus.COMPLETED ? parse(reply.output(),
                ItineraryAnswer.Day.class) : null;
        if (answer == null || answer.items() == null) {
            // The day stays as it was; the travellers can ask again.
            log.warn("Itinerary day not rewritten [itineraryId={}, day={}, error={}]", itinerary.getId(),
                    day.getDayNumber(), reply.error());
            return;
        }
        day.getItems().clear();
        fillDay(day, answer, knownPlaceIds(answer.items()));
        log.info("Itinerary day rewritten [itineraryId={}, day={}]", itinerary.getId(), day.getDayNumber());
    }

    // Items keep their place only if it is one we know and travellers can go to.
    private void fillDay(ItineraryDay day, ItineraryAnswer.Day answer, Set<UUID> knownPlaceIds) {
        day.setTitle(truncate(answer.title() == null || answer.title().isBlank() ? "Day " + day.getDayNumber()
                : answer.title(), 200));
        day.setSummary(blankToNull(answer.summary()));
        int position = 0;
        for (ItineraryAnswer.Item answered : answer.items() == null ? List.<ItineraryAnswer.Item>of()
                : answer.items()) {
            if (answered.title() == null || answered.title().isBlank()) {
                continue;
            }
            ItineraryItem item = new ItineraryItem();
            item.setDay(day);
            item.setPosition(position++);
            item.setTimeOfDay(timeOfDay(answered.timeOfDay()));
            item.setTitle(truncate(answered.title(), 200));
            item.setDescription(blankToNull(answered.description()));
            UUID placeId = uuidOf(answered.placeId());
            item.setPlaceId(placeId != null && knownPlaceIds.contains(placeId) ? placeId : null);
            item.setDurationMinutes(answered.durationMinutes() > 0 ? answered.durationMinutes() : null);
            item.setTips(blankToNull(answered.tips()));
            day.getItems().add(item);
        }
    }

    private Set<UUID> knownPlaceIds(List<ItineraryAnswer.Item> items) {
        Set<UUID> ids = items.stream().map(item -> uuidOf(item.placeId())).filter(id -> id != null)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Set.of();
        }
        return placeRepository.findAllById(ids).stream().filter(Place::isListed).map(Place::getId)
                .collect(Collectors.toSet());
    }

    private void fail(Itinerary itinerary, String error) {
        itinerary.setStatus(ItineraryStatus.FAILED);
        itinerary.setError(error);
        TripPlan plan = itinerary.getPlan();
        quotaRepository.giveBack(plan.getOwnerType(), plan.getOwnerId(), properties.itineraryQuotaEntitlement(),
                period(itinerary.getRequestedAt() == null ? clock.instant() : itinerary.getRequestedAt()));
        notifier.itineraryFinished(itinerary);
        log.warn("Itinerary failed [id={}, error={}]", itinerary.getId(), error);
    }

    // The destinations and, within each, its best known places to visit, for the AI to pick from and link to.
    private Collection<Place> candidatesFor(List<Place> destinations, TripPlan plan) {
        Map<UUID, Place> candidates = new LinkedHashMap<>();
        destinations.forEach(place -> candidates.put(place.getId(), place));
        Set<UUID> ids = new HashSet<>();
        for (Place destination : destinations) {
            PlaceSearchQuery query = new PlaceSearchQuery(null, null, null, null, null, destination.getId(),
                    destination.getPath(), null, null, null, null, null, null, null, null, null, false);
            placeSearchRepository.search(query, PageRequest.of(0, properties.candidatesPerPlace())).getContent()
                    .stream().map(PlaceSearchHit::placeId).forEach(ids::add);
        }
        ids.addAll(plan.getMustSeePlaceIds());
        placeRepository.findAllById(ids).stream().filter(Place::isListed)
                .forEach(place -> candidates.putIfAbsent(place.getId(), place));
        return candidates.values();
    }

    private List<Place> destinationsOf(TripPlan plan) {
        Map<UUID, Place> byId = placeRepository.findAllById(plan.getPlaceIds()).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        return plan.getPlaceIds().stream().filter(byId::containsKey).map(byId::get).toList();
    }

    private List<Place> placesOf(Collection<UUID> ids) {
        return ids.isEmpty() ? List.of() : placeRepository.findAllById(ids);
    }

    private void send(UUID jobId, String task, String languageCode, String prompt, String schemaName,
                      String schema) {
        AiTextRequestedEvent event = new AiTextRequestedEvent(jobId,
                AiTextCompletedContract.AI_TEXT_COMPLETED_DESTINATION_QUEUE_NAME, task, null,
                ItineraryPrompts.system(languageCode), prompt, schemaName, schema, false, 12_000);
        messageProducer.publishAfterCommit(event, "",
                AiTextRequestedContract.AI_TEXT_REQUESTED_QUEUE_NAME);
    }

    private void requireWithinTokenLimit() {
        if (usageRepository.tokensOn(LocalDate.now(clock.withZone(ZoneOffset.UTC)))
                >= contentProperties.dailyTokenHardLimit()) {
            throw new IllegalStateException("AI planning is paused for today; try again tomorrow");
        }
    }

    private void requireUserShare(UUID userId) {
        if (usageRepository.countGeneration(userId, LocalDate.now(clock.withZone(ZoneOffset.UTC)))
                > contentProperties.maxGenerationsPerUserPerDay()) {
            throw new IllegalStateException("You've used today's AI requests; try again tomorrow");
        }
    }

    private Itinerary lockReady(UUID itineraryId) {
        Itinerary itinerary = itineraryRepository.lockById(itineraryId).orElseThrow(() -> notFound(itineraryId));
        if (itinerary.getStatus() != ItineraryStatus.READY) {
            throw new IllegalStateException("The itinerary is %s".formatted(itinerary.getStatus()));
        }
        return itinerary;
    }

    private static ItineraryDay dayOf(Itinerary itinerary, int dayNumber) {
        return itinerary.getDays().stream().filter(day -> day.getDayNumber() == dayNumber).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Day %d not found".formatted(dayNumber)));
    }

    private <T> T parse(String json, Class<T> type) {
        try {
            return json == null ? null : objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("AI answer not readable as {}", type.getSimpleName(), e);
            return null;
        }
    }

    ItineraryResponse toResponse(Itinerary itinerary) {
        Set<UUID> placeIds = itinerary.getDays().stream().flatMap(day -> day.getItems().stream())
                .map(ItineraryItem::getPlaceId).filter(id -> id != null).collect(Collectors.toSet());
        Map<UUID, Place> places = placeIds.isEmpty() ? Map.of() : placeRepository.findAllById(placeIds).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        List<ItineraryDayResponse> days = new ArrayList<>();
        for (ItineraryDay day : itinerary.getDays()) {
            days.add(new ItineraryDayResponse(day.getId(), day.getDayNumber(), day.getDate(), day.getTitle(),
                    day.getSummary(), day.getPendingJobId() != null, day.getItems().stream()
                    .map(item -> new ItineraryItemResponse(item.getId(), item.getPosition(), item.getTimeOfDay(),
                            item.getTitle(), item.getDescription(), item.getPlaceId() == null
                            || !places.containsKey(item.getPlaceId()) ? null
                            : placeMapper.toRef(places.get(item.getPlaceId())), item.getDurationMinutes(),
                            item.getTips()))
                    .toList()));
        }
        return new ItineraryResponse(itinerary.getId(), itinerary.getPlan().getId(), itinerary.getStatus(),
                itinerary.getLanguageCode(), itinerary.getSummary(), days, itinerary.getModel(), itinerary.getError(),
                itinerary.getRequestedByUserId(), itinerary.getCreatedAt(), itinerary.getGeneratedAt(), true);
    }

    private static TimeOfDay timeOfDay(String value) {
        try {
            return value == null ? TimeOfDay.MORNING : TimeOfDay.valueOf(value);
        } catch (IllegalArgumentException e) {
            return TimeOfDay.MORNING;
        }
    }

    private static UUID uuidOf(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String period(Instant at) {
        return YearMonth.from(at.atZone(ZoneOffset.UTC)).toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String truncate(String value, int length) {
        String stripped = value.strip();
        return stripped.length() <= length ? stripped : stripped.substring(0, length);
    }

    private static ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Itinerary with id=%s not found".formatted(id));
    }
}
