package com.triphuddle.api.destination.repository;

import com.triphuddle.api.destination.entity.Itinerary;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link Itinerary}.
 */
@Repository
public interface ItineraryRepository extends BaseJpaRepository<Itinerary, UUID> {

    /**
     * Get a plan's itineraries, the newest first.
     *
     * @param planId the ID of the plan
     * @return the itineraries
     */
    @Query("select i from Itinerary i where i.plan.id = :planId and i.deletedAt is null order by i.createdAt desc")
    List<Itinerary> findForPlan(@Param("planId") UUID planId);

    /**
     * Check if a plan has an itinerary being written.
     *
     * @param planId the ID of the plan
     * @return true if one is GENERATING
     */
    @Query("""
            select count(i) > 0 from Itinerary i where i.plan.id = :planId and i.deletedAt is null
              and i.status = com.triphuddle.api.destination.entity.ItineraryStatus.GENERATING
            """)
    boolean isGenerating(@Param("planId") UUID planId);

    /**
     * Lock an itinerary.
     *
     * @param id the ID of the itinerary
     * @return the locked itinerary
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Itinerary i where i.id = :id and i.deletedAt is null")
    Optional<Itinerary> lockById(@Param("id") UUID id);

    /**
     * Lock the itinerary a worker's answer is for.
     *
     * @param jobId the job's ID
     * @return the locked itinerary, or empty if no longer waited for
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Itinerary i where i.pendingJobId = :jobId")
    Optional<Itinerary> lockByPendingJobId(@Param("jobId") UUID jobId);

    /**
     * Lock the itinerary whose day a worker's answer is for.
     *
     * @param jobId the job's ID
     * @return the locked itinerary, or empty if no longer waited for
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d.itinerary from ItineraryDay d where d.pendingJobId = :jobId")
    Optional<Itinerary> lockByPendingDayJobId(@Param("jobId") UUID jobId);

    /**
     * Clear the rewrites of days whose answer is overdue, so the travellers can ask again.
     *
     * @param requestedBefore rewrites asked for before this are overdue
     * @return how many were cleared
     */
    @Modifying
    @Query("update ItineraryDay d set d.pendingJobId = null where d.pendingJobId is not null"
            + " and d.requestedAt < :requestedBefore")
    int clearOverdueDays(@Param("requestedBefore") Instant requestedBefore);

    /**
     * Lock itineraries whose answer is overdue, skipping those another instance holds.
     *
     * @param requestedBefore asked for before this
     * @param pageable the batch
     * @return the itineraries
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // -2 is SKIP LOCKED, so instances running the job at once split the work instead of waiting.
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select i from Itinerary i where i.pendingJobId is not null and i.requestedAt < :requestedBefore")
    List<Itinerary> lockOverdue(@Param("requestedBefore") Instant requestedBefore, Pageable pageable);
}
