package com.triphuddle.api.destination.visa;

import com.triphuddle.api.destination.config.VisaProperties;
import com.triphuddle.api.destination.dto.VisaRuleImportResponse;
import com.triphuddle.api.destination.entity.ImportStatus;
import com.triphuddle.api.destination.entity.VisaRuleImport;
import com.triphuddle.api.destination.repository.VisaRuleImportRepository;
import com.triphuddle.api.destination.repository.VisaRuleRepository;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.filter.PaginationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Loads the visa rules from the passport-index dataset when staff ask, one import at a time.
 */
@Slf4j
@Service
public class VisaRuleImportService {

    private final VisaRuleImportRepository importRepository;
    private final VisaRuleRepository ruleRepository;
    private final VisaDatasetClient datasetClient;
    private final VisaProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public VisaRuleImportService(VisaRuleImportRepository importRepository, VisaRuleRepository ruleRepository,
                                 VisaDatasetClient datasetClient, VisaProperties properties,
                                 PlatformTransactionManager transactionManager, Clock clock) {
        this.importRepository = importRepository;
        this.ruleRepository = ruleRepository;
        this.datasetClient = datasetClient;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Queue an import of the dataset; it runs within a minute or so.
     *
     * @param userId the staff member asking
     * @return the import, QUEUED
     */
    public VisaRuleImportResponse queue(UUID userId) {
        return transaction.execute(status -> {
            Instant now = clock.instant();
            for (VisaRuleImport active : importRepository.findByStatusIn(
                    EnumSet.of(ImportStatus.QUEUED, ImportStatus.RUNNING))) {
                boolean isDead = active.getStatus() == ImportStatus.RUNNING && active.getStartedAt() != null
                        && active.getStartedAt().isBefore(now.minus(
                        Duration.ofMinutes(properties.runningTimeoutMinutes())));
                if (!isDead) {
                    throw new IllegalStateException("A visa rule import is already waiting or running");
                }
                finish(active, ImportStatus.FAILED, "Stopped without finishing; taken for dead", now);
            }
            VisaRuleImport created = new VisaRuleImport();
            created.setStatus(ImportStatus.QUEUED);
            created.setSourceUrl(properties.datasetUrl());
            created.setRequestedByUserId(userId);
            importRepository.saveAndFlush(created);
            log.info("Visa rule import queued [id={}, userId={}]", created.getId(), userId);
            return toResponse(created);
        });
    }

    /**
     * Run the oldest waiting import, if any.
     *
     * @return the import run, or null if none was waiting
     */
    public VisaRuleImportResponse runNext() {
        VisaRuleImport started = transaction.execute(status -> {
            List<VisaRuleImport> waiting = importRepository.lockWaiting(ImportStatus.QUEUED, PageRequest.of(0, 1));
            if (waiting.isEmpty()) {
                return null;
            }
            VisaRuleImport next = waiting.get(0);
            next.setStatus(ImportStatus.RUNNING);
            next.setStartedAt(clock.instant());
            return importRepository.save(next);
        });
        if (started == null) {
            return null;
        }
        UUID id = started.getId();
        try {
            // Downloaded outside a transaction, so a slow host holds no connection.
            VisaDatasetParser.Parsed parsed = VisaDatasetParser.parse(datasetClient.download(started.getSourceUrl()));
            return transaction.execute(status -> {
                Instant now = clock.instant();
                ruleRepository.upsertAll(parsed.rules(), id, now);
                VisaRuleImport running = importRepository.findById(id).orElseThrow();
                running.setRuleCount(parsed.rules().size());
                running.setSkippedCount(parsed.skipped());
                if (parsed.rules().size() >= properties.minRulesForFullImport()) {
                    running.setRemovedCount(ruleRepository.deleteNotLoadedBy(id));
                } else {
                    log.warn("Visa dataset smaller than a full one; nothing removed [id={}, rules={}]", id,
                            parsed.rules().size());
                }
                finish(running, ImportStatus.COMPLETED, null, now);
                log.info("Visa rules imported [id={}, rules={}, skipped={}, removed={}]", id,
                        running.getRuleCount(), running.getSkippedCount(), running.getRemovedCount());
                return toResponse(running);
            });
        } catch (RuntimeException e) {
            log.error("Visa rule import failed [id={}]", id, e);
            return transaction.execute(status -> {
                VisaRuleImport failed = importRepository.findById(id).orElseThrow();
                finish(failed, ImportStatus.FAILED, e.getMessage(), clock.instant());
                return toResponse(failed);
            });
        }
    }

    /**
     * Get the imports, the newest first.
     *
     * @param filter the page
     * @return a page of imports
     */
    public Paginated<VisaRuleImportResponse> getAll(PaginationFilter filter) {
        return transaction.execute(status -> Paginated.of(
                importRepository.findByDeletedAtIsNullOrderByCreatedAtDesc(filter.toPageable()),
                jobs -> jobs.stream().map(VisaRuleImportService::toResponse).toList()));
    }

    private void finish(VisaRuleImport job, ImportStatus status, String error, Instant now) {
        job.setStatus(status);
        job.setError(error);
        job.setFinishedAt(now);
        // Flushed at once: Hibernate inserts before it updates, so a new import would otherwise meet this one
        // still active.
        importRepository.saveAndFlush(job);
    }

    private static VisaRuleImportResponse toResponse(VisaRuleImport job) {
        return new VisaRuleImportResponse(job.getId(), job.getStatus(), job.getSourceUrl(), job.getRequestedByUserId(),
                job.getStartedAt(), job.getFinishedAt(), job.getRuleCount(), job.getSkippedCount(),
                job.getRemovedCount(), job.getError(), job.getCreatedAt());
    }
}
