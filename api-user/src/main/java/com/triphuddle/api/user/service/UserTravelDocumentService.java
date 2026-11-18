package com.triphuddle.api.user.service;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.user.UserTravelDocumentsUpdatedContract;
import com.triphuddle.amqp.user.UserTravelDocumentsUpdatedEvent;
import com.triphuddle.amqp.user.UserTravelDocumentsUpdatedEvent.ResidencePermit;
import com.triphuddle.api.user.dto.ResidencePermitRequest;
import com.triphuddle.api.user.dto.TravelDocumentsRequest;
import com.triphuddle.api.user.dto.TravelDocumentsResponse;
import com.triphuddle.api.user.entity.TravelDocumentType;
import com.triphuddle.api.user.entity.UserTravelDocument;
import com.triphuddle.api.user.repository.UserTravelDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the caller's nationalities and residence permits, and tells api-destination, which gives visa guidance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserTravelDocumentService {

    private static final Set<String> COUNTRY_CODES = Set.of(Locale.getISOCountries());
    // Kosovo has no ISO code yet but does issue passports; the visa dataset uses XK for it.
    private static final String KOSOVO = "XK";

    private final UserTravelDocumentRepository repository;
    private final RabbitMQMessageProducer messageProducer;

    /**
     * Get a user's nationalities and residence permits.
     *
     * @param userId the ID of the user
     * @return the documents
     */
    @Transactional(readOnly = true)
    public TravelDocumentsResponse get(UUID userId) {
        return toResponse(repository.findByUserIdOrderByTypeAscCountryCodeAsc(userId));
    }

    /**
     * Replace a user's nationalities and residence permits, then publish them.
     *
     * @param userId the ID of the user
     * @param request every document they hold
     * @return the documents
     */
    @Transactional
    public TravelDocumentsResponse replace(UUID userId, TravelDocumentsRequest request) {
        Set<String> nationalities = new LinkedHashSet<>();
        for (String code : request.getNationalities()) {
            nationalities.add(country(code));
        }
        Set<String> permitCountries = new LinkedHashSet<>();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (ResidencePermitRequest permit : request.getResidencePermits()) {
            String code = country(permit.getCountryCode());
            if (nationalities.contains(code)) {
                throw new IllegalStateException("A citizen of %s needs no residence permit there".formatted(code));
            }
            if (!permitCountries.add(code)) {
                throw new IllegalStateException("Give one residence permit per country, not two for " + code);
            }
            if (permit.getValidUntil() != null && permit.getValidUntil().isBefore(today)) {
                throw new IllegalStateException("The residence permit for %s has expired".formatted(code));
            }
        }

        repository.deleteByUserId(userId);
        repository.flush();
        nationalities.forEach(code -> repository.save(document(userId, TravelDocumentType.NATIONALITY, code)));
        for (ResidencePermitRequest permit : request.getResidencePermits()) {
            UserTravelDocument document = document(userId, TravelDocumentType.RESIDENCE_PERMIT,
                    country(permit.getCountryCode()));
            document.setPermitKind(permit.getKind());
            document.setValidUntil(permit.getValidUntil());
            repository.save(document);
        }

        TravelDocumentsResponse response = toResponse(repository.findByUserIdOrderByTypeAscCountryCodeAsc(userId));
        publish(userId, response, Instant.now(Clock.systemUTC()));
        log.info("Travel documents updated [userId={}, nationalities={}, permits={}]", userId,
                response.nationalities().size(), response.residencePermits().size());
        return response;
    }

    /**
     * Forget a deleted user's documents; api-destination drops its copy on the user-deleted event.
     *
     * @param userId the ID of the user
     */
    @Transactional
    public void deleteForUser(UUID userId) {
        int removed = repository.deleteByUserId(userId);
        if (removed > 0) {
            log.info("Travel documents removed [userId={}, count={}]", userId, removed);
        }
    }

    private void publish(UUID userId, TravelDocumentsResponse documents, Instant changedAt) {
        UserTravelDocumentsUpdatedEvent event = new UserTravelDocumentsUpdatedEvent(userId,
                documents.nationalities(), documents.residencePermits(), changedAt);
        // Only once saved, so api-destination never hears of documents that were rolled back.
        messageProducer.publishAfterCommit(event, "",
                UserTravelDocumentsUpdatedContract.USER_TRAVEL_DOCUMENTS_UPDATED_QUEUE_NAME);
    }

    private static UserTravelDocument document(UUID userId, TravelDocumentType type, String countryCode) {
        UserTravelDocument document = new UserTravelDocument();
        document.setUserId(userId);
        document.setType(type);
        document.setCountryCode(countryCode);
        return document;
    }

    private static String country(String code) {
        String upper = code.strip().toUpperCase(Locale.ROOT);
        if (!COUNTRY_CODES.contains(upper) && !KOSOVO.equals(upper)) {
            throw new IllegalStateException("Unknown country code " + code);
        }
        return upper;
    }

    private static TravelDocumentsResponse toResponse(List<UserTravelDocument> documents) {
        List<String> nationalities = documents.stream()
                .filter(document -> document.getType() == TravelDocumentType.NATIONALITY)
                .map(UserTravelDocument::getCountryCode).toList();
        List<ResidencePermit> permits = documents.stream()
                .filter(document -> document.getType() == TravelDocumentType.RESIDENCE_PERMIT)
                .map(document -> new ResidencePermit(document.getCountryCode(), document.getPermitKind(),
                        document.getValidUntil()))
                .toList();
        return new TravelDocumentsResponse(nationalities, permits);
    }
}
