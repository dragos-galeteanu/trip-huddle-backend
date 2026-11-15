package com.triphuddle.api.destination.visa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.ai.AiJobStatus;
import com.triphuddle.amqp.ai.AiTextCompletedContract;
import com.triphuddle.amqp.ai.AiTextCompletedEvent;
import com.triphuddle.amqp.ai.AiTextRequestedContract;
import com.triphuddle.amqp.ai.AiTextRequestedEvent;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.amqp.user.UserTravelDocumentsUpdatedEvent.ResidencePermit;
import com.triphuddle.api.destination.config.ContentProperties;
import com.triphuddle.api.destination.config.VisaProperties;
import com.triphuddle.api.destination.content.ContentService;
import com.triphuddle.api.destination.dto.TravellerVisaResponse;
import com.triphuddle.api.destination.dto.TripVisaResponse;
import com.triphuddle.api.destination.dto.VisaCheckResponse;
import com.triphuddle.api.destination.dto.VisaGuidanceResponse;
import com.triphuddle.api.destination.dto.VisaRuleResponse;
import com.triphuddle.api.destination.entity.ContentStatus;
import com.triphuddle.api.destination.entity.ImportStatus;
import com.triphuddle.api.destination.entity.Place;
import com.triphuddle.api.destination.entity.TravellerDocuments;
import com.triphuddle.api.destination.entity.TripPlan;
import com.triphuddle.api.destination.entity.VisaGuidance;
import com.triphuddle.api.destination.entity.VisaRequirement;
import com.triphuddle.api.destination.repository.AiUsageRepository;
import com.triphuddle.api.destination.repository.GroupMemberRepository;
import com.triphuddle.api.destination.repository.PlaceRepository;
import com.triphuddle.api.destination.repository.TravellerDocumentsRepository;
import com.triphuddle.api.destination.repository.VisaGuidanceRepository;
import com.triphuddle.api.destination.repository.VisaRule;
import com.triphuddle.api.destination.repository.VisaRuleImportRepository;
import com.triphuddle.api.destination.repository.VisaRuleRepository;
import com.triphuddle.api.destination.service.AfterCommit;
import com.triphuddle.api.destination.trip.TripPlanService;
import com.triphuddle.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tells each traveller what they need to enter a trip's countries: the dataset's rule for their easiest passport,
 * explained by the openai worker with official sources, and shared by everyone in the same case.
 */
@Slf4j
@Service
@Transactional
public class VisaGuidanceService {

    static final String TASK = "visa-guidance";
    static final String DISCLAIMER = "Entry rules change. This guidance is written by AI from public sources and is "
            + "not legal advice: check the official links before you travel.";

    private static final String ADMIN_ROLE = "ADMIN";
    private static final Comparator<VisaRule> EASIEST = Comparator
            .comparing(VisaRule::requirement)
            .thenComparing(rule -> rule.allowedDays() == null ? 0 : -rule.allowedDays());

    private final VisaRuleRepository ruleRepository;
    private final VisaRuleImportRepository importRepository;
    private final VisaGuidanceRepository guidanceRepository;
    private final TravellerDocumentsRepository documentsRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final PlaceRepository placeRepository;
    private final TripPlanService planService;
    private final AiUsageRepository usageRepository;
    private final RabbitMQMessageProducer messageProducer;
    private final VisaProperties properties;
    private final ContentProperties contentProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public VisaGuidanceService(VisaRuleRepository ruleRepository, VisaRuleImportRepository importRepository,
                               VisaGuidanceRepository guidanceRepository,
                               TravellerDocumentsRepository documentsRepository,
                               GroupMemberRepository groupMemberRepository, PlaceRepository placeRepository,
                               TripPlanService planService, AiUsageRepository usageRepository,
                               RabbitMQMessageProducer messageProducer, VisaProperties properties,
                               ContentProperties contentProperties, ObjectMapper objectMapper, Clock clock) {
        this.ruleRepository = ruleRepository;
        this.importRepository = importRepository;
        this.guidanceRepository = guidanceRepository;
        this.documentsRepository = documentsRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.placeRepository = placeRepository;
        this.planService = planService;
        this.usageRepository = usageRepository;
        this.messageProducer = messageProducer;
        this.properties = properties;
        this.contentProperties = contentProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Get what a trip's travellers need: everyone for a group's admins and staff, otherwise the caller alone.
     *
     * @param planId the ID of the plan
     * @param viewerId the caller
     * @param isStaff whether the caller is staff
     * @param language the language asked for, or null for the plan's
     * @return the checks; guidance still being written comes back GENERATING, so ask again
     */
    public TripVisaResponse forPlan(UUID planId, UUID viewerId, boolean isStaff, String language) {
        TripPlan plan = planService.find(planId);
        String languageCode = ContentService.normalise(language != null ? language : plan.getLanguageCode());
        List<String> countries = countriesOf(plan);

        List<UUID> travellers = plan.getOwnerType() == LicenseTarget.USER ? List.of(plan.getOwnerId())
                : groupMemberRepository.findUserIdsByGroupId(plan.getOwnerId());
        boolean isSeeingEveryone = isStaff || plan.getOwnerType() == LicenseTarget.USER
                || groupMemberRepository.existsByGroupIdAndUserIdAndRole(plan.getOwnerId(), viewerId, ADMIN_ROLE);
        List<UUID> shown = isSeeingEveryone ? travellers
                : travellers.stream().filter(viewerId::equals).toList();

        Map<UUID, TravellerDocuments> documents = documentsRepository.findByUserIdInAndDeletedAtIsNull(shown).stream()
                .collect(Collectors.toMap(TravellerDocuments::getUserId, Function.identity()));
        Budget budget = new Budget(viewerId);
        Map<String, VisaGuidanceResponse> written = new HashMap<>();
        List<TravellerVisaResponse> responses = new ArrayList<>();
        for (UUID userId : shown) {
            TravellerDocuments held = documents.get(userId);
            List<String> nationalities = held == null ? List.of() : held.getNationalities();
            List<ResidencePermit> permits = held == null ? List.of() : held.getResidencePermits();
            // A permit counts only if it lasts the whole trip.
            List<String> permitCodes = permits.stream()
                    .filter(permit -> permit.validUntil() == null || !permit.validUntil().isBefore(plan.getEndDate()))
                    .map(ResidencePermit::countryCode).distinct().sorted().toList();
            List<VisaCheckResponse> checks = countries.stream()
                    .map(country -> check(nationalities, permitCodes, country, languageCode, budget, written))
                    .toList();
            responses.add(new TravellerVisaResponse(userId, nationalities, permits, nationalities.isEmpty(), checks));
        }
        return new TripVisaResponse(plan.getId(), countries, isSeeingEveryone, responses, rulesUpdatedAt(),
                DISCLAIMER);
    }

    /**
     * Get what one passport needs for a country, without residence permits.
     *
     * @param passportCode the passport's country
     * @param destinationCode the destination
     * @param language the language, or null for English
     * @param userId the caller
     * @return the check
     */
    public VisaCheckResponse check(String passportCode, String destinationCode, String language, UUID userId) {
        String passport = code(passportCode);
        String destination = code(destinationCode);
        return check(List.of(passport), List.of(), destination, ContentService.normalise(language),
                new Budget(userId), new HashMap<>());
    }

    /**
     * Apply the worker's answer to the guidance waiting for it; an answer no longer waited for only counts its
     * tokens.
     *
     * @param reply the answer
     */
    public void onAnswer(AiTextCompletedEvent reply) {
        Instant now = clock.instant();
        usageRepository.addTokens(LocalDate.ofInstant(now, ZoneOffset.UTC), reply.inputTokens(),
                reply.outputTokens());
        Optional<VisaGuidance> waiting = guidanceRepository.lockByPendingJobId(reply.jobId());
        if (waiting.isEmpty()) {
            log.info("Answer for visa guidance no longer waited for [jobId={}]", reply.jobId());
            return;
        }
        VisaGuidance guidance = waiting.get();
        guidance.setPendingJobId(null);
        String error = reply.status() == AiJobStatus.COMPLETED ? validate(reply.output()) : reply.error();
        if (error != null) {
            guidance.setLastError(error);
            guidance.setStatus(guidance.getContent() == null ? ContentStatus.FAILED : ContentStatus.READY);
            log.warn("Visa guidance failed [key={}, error={}]", guidance.getGuidanceKey(), error);
            return;
        }
        guidance.setContent(reply.output());
        guidance.setCitations(new ArrayList<>(reply.citations() == null ? List.of() : reply.citations()));
        guidance.setModel(reply.model());
        guidance.setGeneratedAt(now);
        guidance.setExpiresAt(now.plus(Duration.ofDays(properties.guidanceFreshDays())));
        guidance.setStatus(ContentStatus.READY);
        guidance.setLastError(null);
        log.info("Visa guidance written [key={}, model={}]", guidance.getGuidanceKey(), reply.model());
    }

    /**
     * Give up on guidance whose answer is overdue, so the next read asks again.
     *
     * @return how many were given up on
     */
    public int giveUpOverdue() {
        return guidanceRepository.giveUpOverdue(
                clock.instant().minus(Duration.ofMinutes(properties.pendingTimeoutMinutes())));
    }

    private VisaCheckResponse check(List<String> nationalities, List<String> permitCodes, String destination,
                                    String languageCode, Budget budget, Map<String, VisaGuidanceResponse> written) {
        String name = VisaPrompts.country(destination);
        if (nationalities.isEmpty()) {
            return new VisaCheckResponse(destination, name, null, null, null, List.of(), permitCodes, true, null);
        }
        Map<String, VisaRule> rules = ruleRepository.find(nationalities, destination).stream()
                .collect(Collectors.toMap(VisaRule::passportCode, Function.identity()));
        List<VisaRuleResponse> byNationality = nationalities.stream().map(passport -> {
            VisaRule rule = rules.get(passport);
            if (passport.equals(destination)) {
                return new VisaRuleResponse(passport, VisaRequirement.CITIZEN, null);
            }
            return new VisaRuleResponse(passport, rule == null ? null : rule.requirement(),
                    rule == null ? null : rule.allowedDays());
        }).toList();

        Optional<VisaRule> easiest = Stream.concat(rules.values().stream(), nationalities.stream()
                        .filter(destination::equals)
                        .map(passport -> new VisaRule(passport, destination, VisaRequirement.CITIZEN, null)))
                .min(EASIEST);
        String passport = easiest.map(VisaRule::passportCode).orElse(nationalities.get(0));
        VisaRequirement requirement = easiest.map(VisaRule::requirement).orElse(null);
        Integer allowedDays = easiest.map(VisaRule::allowedDays).orElse(null);
        VisaGuidanceResponse guidance;
        if (requirement == VisaRequirement.CITIZEN) {
            guidance = new VisaGuidanceResponse("NOT_NEEDED", null, List.of(), languageCode, null, null, false,
                    false);
        } else {
            String key = String.join("|", destination, passport, requirement == null ? "UNKNOWN" : requirement.name(),
                    String.join(",", permitCodes), languageCode);
            guidance = written.computeIfAbsent(key, ignored -> guidance(key, destination, passport, requirement,
                    allowedDays, permitCodes, languageCode, budget));
        }
        return new VisaCheckResponse(destination, name, passport, requirement, allowedDays, byNationality,
                permitCodes, false, guidance);
    }

    private VisaGuidanceResponse guidance(String key, String destination, String passport,
                                          VisaRequirement requirement, Integer allowedDays, List<String> permitCodes,
                                          String languageCode, Budget budget) {
        Instant now = clock.instant();
        guidanceRepository.insertIfAbsent(key, destination, passport, String.join(",", permitCodes),
                requirement == null ? null : requirement.name(), allowedDays, languageCode, now);
        VisaGuidance guidance = guidanceRepository.lockByKey(key).orElseThrow();
        boolean hasContent = guidance.getContent() != null;

        if (guidance.getPendingJobId() != null) {
            return view(guidance, hasContent ? "READY" : "GENERATING", hasContent);
        }
        boolean isFresh = hasContent && (guidance.getExpiresAt() == null || guidance.getExpiresAt().isAfter(now));
        if (isFresh) {
            return view(guidance, "READY", false);
        }
        boolean isRecentFailure = !hasContent && guidance.getStatus() == ContentStatus.FAILED
                && guidance.getRequestedAt() != null && guidance.getRequestedAt().isAfter(
                now.minus(Duration.ofMinutes(properties.retryFailedAfterMinutes())));
        if (isRecentFailure) {
            return view(guidance, "FAILED", false);
        }
        if (!budget.allowsAnother()) {
            return view(guidance, hasContent ? "READY" : "UNAVAILABLE", false);
        }

        guidance.setPendingJobId(UUID.randomUUID());
        guidance.setRequestedAt(now);
        if (!hasContent) {
            guidance.setStatus(ContentStatus.PENDING);
        }
        AiTextRequestedEvent event = new AiTextRequestedEvent(guidance.getPendingJobId(),
                AiTextCompletedContract.AI_TEXT_COMPLETED_DESTINATION_QUEUE_NAME, TASK, null,
                VisaPrompts.system(languageCode),
                VisaPrompts.user(passport, destination, requirement, allowedDays, permitCodes), "visa_guidance",
                VisaSchemas.GUIDANCE, true, 3000);
        AfterCommit.run(() -> messageProducer.publish(event, "", AiTextRequestedContract.AI_TEXT_REQUESTED_QUEUE_NAME));
        log.info("Visa guidance requested [key={}]", key);
        return view(guidance, hasContent ? "READY" : "GENERATING", hasContent);
    }

    private VisaGuidanceResponse view(VisaGuidance guidance, String state, boolean isRefreshing) {
        return new VisaGuidanceResponse(state, json(guidance.getContent()), guidance.getCitations(),
                guidance.getLanguageCode(), guidance.getModel(), guidance.getGeneratedAt(), isRefreshing,
                guidance.getContent() != null);
    }

    // The trip's countries, in the order its places are listed.
    private List<String> countriesOf(TripPlan plan) {
        List<UUID> ids = Stream.concat(plan.getPlaceIds().stream(), plan.getMustSeePlaceIds().stream()).toList();
        Map<UUID, String> codes = placeRepository.findAllById(ids).stream()
                .filter(place -> place.getCountryCode() != null)
                .collect(Collectors.toMap(Place::getId, Place::getCountryCode));
        Set<String> countries = new LinkedHashSet<>();
        ids.stream().map(codes::get).filter(code -> code != null).forEach(countries::add);
        return List.copyOf(countries);
    }

    private Instant rulesUpdatedAt() {
        return importRepository.findFirstByStatusOrderByFinishedAtDesc(ImportStatus.COMPLETED)
                .map(job -> job.getFinishedAt()).orElse(null);
    }

    private String validate(String output) {
        JsonNode node = json(output);
        if (node == null || !node.path("summary").isTextual() || !node.path("steps").isArray()) {
            return "The answer was not visa guidance";
        }
        return null;
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

    private static String code(String countryCode) {
        String code = countryCode == null ? "" : countryCode.strip().toUpperCase(Locale.ROOT);
        if (!code.matches("^[A-Z]{2}$")) {
            throw new ResourceNotFoundException("Country %s not found".formatted(countryCode));
        }
        return code;
    }

    // Each job a read sends counts once towards the reader's daily share, and none is sent past the hard limit.
    private final class Budget {
        private final UUID userId;
        private Boolean isExhausted;

        private Budget(UUID userId) {
            this.userId = userId;
        }

        private boolean allowsAnother() {
            if (Boolean.TRUE.equals(isExhausted)) {
                return false;
            }
            LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
            boolean isOver = usageRepository.tokensOn(today) >= contentProperties.dailyTokenHardLimit()
                    || (userId != null && usageRepository.countGeneration(userId, today)
                    > contentProperties.maxGenerationsPerUserPerDay());
            isExhausted = isOver;
            return !isOver;
        }
    }
}
