package com.triphuddle.api.destination.repository;

import com.triphuddle.api.destination.entity.ImportJob;
import com.triphuddle.api.destination.entity.ImportStatus;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link ImportJob}.
 */
@Repository
public interface ImportJobRepository extends BaseJpaRepository<ImportJob, UUID> {

    /**
     * Check if a country has an import waiting or running.
     *
     * @param countryCode the country, ISO 3166 alpha-2
     * @return true if one is QUEUED or RUNNING
     */
    @Query("""
            select count(j) > 0 from ImportJob j where j.countryCode = :countryCode
              and j.status in (com.triphuddle.api.destination.entity.ImportStatus.QUEUED,
                               com.triphuddle.api.destination.entity.ImportStatus.RUNNING)
            """)
    boolean isActive(@Param("countryCode") String countryCode);

    /**
     * Take the oldest waiting import, or a running one whose instance stopped renewing its heartbeat, so that
     * one instance runs it.
     *
     * @param staleBefore a heartbeat older than this counts as cut off
     * @param now the time it starts
     * @return the ID of the job taken, if any
     */
    @Query(value = """
            update destination_import_jobs
            set status = 'RUNNING', started_at = coalesce(started_at, :now), heartbeat_at = :now,
                updated_at = :now, error = null
            where id = (select id from destination_import_jobs
                        where status = 'QUEUED' or (status = 'RUNNING' and heartbeat_at < :staleBefore)
                        order by created_at
                        for update skip locked
                        limit 1)
            returning id
            """, nativeQuery = true)
    Optional<UUID> claimNext(@Param("staleBefore") Instant staleBefore, @Param("now") Instant now);

    /**
     * Find imports for staff, newest first.
     *
     * @param countryCode the country, or null for all
     * @param status the status, or null for all
     * @param pageable the page
     * @return a page of imports
     */
    @Query("""
            select j from ImportJob j
            where (:countryCode is null or j.countryCode = :countryCode)
              and (:status is null or j.status = :status)
            order by j.createdAt desc
            """)
    Page<ImportJob> findForStaff(@Param("countryCode") String countryCode, @Param("status") ImportStatus status,
                                 Pageable pageable);
}
