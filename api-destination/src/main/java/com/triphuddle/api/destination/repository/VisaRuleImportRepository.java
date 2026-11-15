package com.triphuddle.api.destination.repository;

import com.triphuddle.api.destination.entity.ImportStatus;
import com.triphuddle.api.destination.entity.VisaRuleImport;
import com.triphuddle.common.repository.BaseJpaRepository;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for visa rule imports.
 */
public interface VisaRuleImportRepository extends BaseJpaRepository<VisaRuleImport, UUID> {

    /**
     * Find imports, the newest first.
     *
     * @param pageable the page
     * @return a page of imports
     */
    Page<VisaRuleImport> findByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Find the imports in some statuses.
     *
     * @param statuses the statuses
     * @return the imports
     */
    List<VisaRuleImport> findByStatusIn(Collection<ImportStatus> statuses);

    /**
     * Find the latest import in a status.
     *
     * @param status e.g. COMPLETED
     * @return the import, if any
     */
    Optional<VisaRuleImport> findFirstByStatusOrderByFinishedAtDesc(ImportStatus status);

    /**
     * Lock the oldest waiting import, skipping one another instance is starting.
     *
     * @param pageable the first one
     * @return the import, if any
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select i from VisaRuleImport i where i.status = :status order by i.createdAt")
    List<VisaRuleImport> lockWaiting(@Param("status") ImportStatus status, Pageable pageable);
}
