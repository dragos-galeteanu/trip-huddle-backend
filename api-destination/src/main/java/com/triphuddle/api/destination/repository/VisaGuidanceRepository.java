package com.triphuddle.api.destination.repository;

import com.triphuddle.api.destination.entity.VisaGuidance;
import com.triphuddle.common.repository.BaseJpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for visa guidance.
 */
public interface VisaGuidanceRepository extends BaseJpaRepository<VisaGuidance, UUID> {

    /**
     * Add an empty guidance for a key unless one exists, so two readers can't both add it.
     *
     * @param key the guidance key
     * @param destinationCode the destination
     * @param passportCode the passport
     * @param permitCodes the permit countries, comma-separated
     * @param requirement the dataset's requirement, or null
     * @param allowedDays the dataset's days, or null
     * @param languageCode the language
     * @param now when
     */
    @Modifying
    @Query(value = """
            insert into destination_visa_guidance (id, created_at, updated_at, guidance_key, destination_code,
                passport_code, permit_codes, requirement, allowed_days, language_code, status, citations)
            values (gen_random_uuid(), :now, :now, :key, :destinationCode, :passportCode, :permitCodes,
                :requirement, :allowedDays, :languageCode, 'PENDING', cast('[]' as jsonb))
            on conflict (guidance_key) do nothing
            """, nativeQuery = true)
    void insertIfAbsent(@Param("key") String key, @Param("destinationCode") String destinationCode,
                        @Param("passportCode") String passportCode, @Param("permitCodes") String permitCodes,
                        @Param("requirement") String requirement, @Param("allowedDays") Integer allowedDays,
                        @Param("languageCode") String languageCode, @Param("now") Instant now);

    /**
     * Lock a guidance by its key.
     *
     * @param key the guidance key
     * @return the guidance, if any
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from VisaGuidance g where g.guidanceKey = :key")
    Optional<VisaGuidance> lockByKey(@Param("key") String key);

    /**
     * Lock the guidance waiting for an answer.
     *
     * @param jobId the ID of the job
     * @return the guidance, if still waiting
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from VisaGuidance g where g.pendingJobId = :jobId")
    Optional<VisaGuidance> lockByPendingJobId(@Param("jobId") UUID jobId);

    /**
     * Give up on answers that are overdue; guidance without content becomes FAILED, so a read tries again.
     *
     * @param requestedBefore jobs sent before this are overdue
     * @return how many were given up on
     */
    @Modifying
    @Query("""
            update VisaGuidance g set g.pendingJobId = null, g.lastError = 'The answer took too long',
                g.status = case when g.content is null then com.triphuddle.api.destination.entity.ContentStatus.FAILED
                    else com.triphuddle.api.destination.entity.ContentStatus.READY end
            where g.pendingJobId is not null and g.requestedAt < :requestedBefore
            """)
    int giveUpOverdue(@Param("requestedBefore") Instant requestedBefore);
}
