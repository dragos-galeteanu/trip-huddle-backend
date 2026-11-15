package com.triphuddle.api.destination.repository;

import com.triphuddle.api.destination.entity.ContentSection;
import com.triphuddle.api.destination.entity.PlaceContent;
import com.triphuddle.common.repository.BaseJpaRepository;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link PlaceContent}.
 */
@Repository
public interface PlaceContentRepository extends BaseJpaRepository<PlaceContent, UUID> {

    /**
     * Create a section's row unless it exists, as two readers may ask for it at once.
     *
     * @return 1 if created, 0 if it was there
     */
    @Modifying
    @Query(value = """
            insert into destination_place_contents (id, created_at, updated_at, place_id, section, language_code,
                status, citations, failed_attempts)
            values (gen_random_uuid(), now(), now(), :placeId, :section, :languageCode, 'PENDING',
                cast('[]' as jsonb), 0)
            on conflict (place_id, section, language_code) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("placeId") UUID placeId, @Param("section") String section,
                       @Param("languageCode") String languageCode);

    /**
     * Get a place's sections in some languages.
     *
     * @param placeId the ID of the place
     * @param languageCodes the languages
     * @return the rows that exist
     */
    @Query("select c from PlaceContent c where c.place.id = :placeId and c.languageCode in :languageCodes")
    List<PlaceContent> findForPlace(@Param("placeId") UUID placeId,
                                    @Param("languageCodes") Collection<String> languageCodes);

    /**
     * Lock a section in a language.
     *
     * @param placeId the ID of the place
     * @param section the section
     * @param languageCode the language
     * @return the locked row
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from PlaceContent c
            where c.place.id = :placeId and c.section = :section and c.languageCode = :languageCode
            """)
    Optional<PlaceContent> lock(@Param("placeId") UUID placeId, @Param("section") ContentSection section,
                                @Param("languageCode") String languageCode);

    /**
     * Lock the section a worker's answer is for.
     *
     * @param jobId the job's ID
     * @return the locked row, or empty if the answer is for a job no longer waited for
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PlaceContent c where c.pendingJobId = :jobId")
    Optional<PlaceContent> lockByPendingJobId(@Param("jobId") UUID jobId);

    /**
     * Mark sections read, at most once in a while each, so reads don't all write.
     *
     * @param ids the rows read
     * @param now the time
     * @param before only rows last read before this
     * @return how many were marked
     */
    @Modifying
    @Query(value = """
            update destination_place_contents set last_viewed_at = :now
            where id in (:ids) and (last_viewed_at is null or last_viewed_at < :before)
            """, nativeQuery = true)
    int markViewed(@Param("ids") Collection<UUID> ids, @Param("now") Instant now, @Param("before") Instant before);

    /**
     * Lock ready sections due for a refresh that were read lately, skipping those another instance holds.
     *
     * @param now the time
     * @param viewedAfter only sections read since then
     * @param pageable the batch
     * @return the sections
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // -2 is SKIP LOCKED, so instances running the job at once split the work instead of waiting.
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select c from PlaceContent c
            where c.pendingJobId is null and c.content is not null and c.expiresAt <= :now
              and c.lastViewedAt >= :viewedAfter
            order by c.expiresAt
            """)
    List<PlaceContent> lockDueForRefresh(@Param("now") Instant now, @Param("viewedAfter") Instant viewedAfter,
                                         Pageable pageable);

    /**
     * Lock sections whose answer is overdue, skipping those another instance holds.
     *
     * @param requestedBefore asked for before this
     * @param pageable the batch
     * @return the sections
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select c from PlaceContent c
            where c.pendingJobId is not null and c.requestedAt < :requestedBefore
            order by c.requestedAt
            """)
    List<PlaceContent> lockOverdue(@Param("requestedBefore") Instant requestedBefore, Pageable pageable);
}
