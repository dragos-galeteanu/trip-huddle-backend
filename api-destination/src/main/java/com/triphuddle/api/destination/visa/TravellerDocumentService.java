package com.triphuddle.api.destination.visa;

import com.triphuddle.amqp.user.UserTravelDocumentsUpdatedEvent;
import com.triphuddle.api.destination.entity.TravellerDocuments;
import com.triphuddle.api.destination.repository.TravellerDocumentsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Keeps the copies of travellers' nationalities and residence permits in step with api-user.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TravellerDocumentService {

    private final TravellerDocumentsRepository repository;
    private final Clock clock;

    /**
     * Apply a user's latest documents; an update older than the copy, or for a deleted user, is dropped.
     *
     * @param event the documents
     */
    public void onUpdated(UserTravelDocumentsUpdatedEvent event) {
        TravellerDocuments copy = repository.lockByUserId(event.userId()).orElseGet(() -> {
            TravellerDocuments created = new TravellerDocuments();
            created.setUserId(event.userId());
            return created;
        });
        if (copy.getDeletedAt() != null
                || (copy.getChangedAt() != null && !event.changedAt().isAfter(copy.getChangedAt()))) {
            log.info("Travel documents dropped as stale [userId={}]", event.userId());
            return;
        }
        copy.setNationalities(new ArrayList<>(event.nationalities() == null ? List.of() : event.nationalities()));
        copy.setResidencePermits(new ArrayList<>(event.residencePermits() == null ? List.of()
                : event.residencePermits()));
        copy.setChangedAt(event.changedAt());
        repository.save(copy);
        log.info("Travel documents copied [userId={}, nationalities={}]", event.userId(),
                copy.getNationalities().size());
    }

    /**
     * Forget a deleted user's documents, keeping an empty row so a late update can't bring them back.
     *
     * @param userId the ID of the user
     */
    public void onUserDeleted(UUID userId) {
        TravellerDocuments copy = repository.lockByUserId(userId).orElseGet(() -> {
            TravellerDocuments created = new TravellerDocuments();
            created.setUserId(userId);
            return created;
        });
        copy.setNationalities(new ArrayList<>());
        copy.setResidencePermits(new ArrayList<>());
        copy.setChangedAt(clock.instant());
        copy.setDeletedAt(clock.instant());
        repository.save(copy);
        log.info("Travel documents forgotten [userId={}]", userId);
    }
}
