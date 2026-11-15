package com.triphuddle.api.payment.repository;

import com.triphuddle.api.payment.entity.StripeEvent;
import com.triphuddle.api.payment.entity.StripeEventStatus;
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
import java.util.UUID;

/**
 * Data access for {@link StripeEvent}.
 */
@Repository
public interface StripeEventRepository extends BaseJpaRepository<StripeEvent, UUID> {

    /**
     * Store an event unless one with its Stripe ID is already stored, so redeliveries are dropped.
     *
     * @return 1 if stored, 0 if it was a duplicate
     */
    // ON CONFLICT rather than catching the unique violation: a failed insert would mark the
    // transaction rollback-only, and two deliveries racing each other must both answer 200.
    @Modifying
    @Query(value = """
            insert into payment_stripe_events (id, created_at, updated_at, stripe_event_id, event_type,
                api_version, is_livemode, stripe_created_at, payload, status, attempts, next_attempt_at)
            values (gen_random_uuid(), now(), now(), :stripeEventId, :eventType, :apiVersion, :isLivemode,
                :stripeCreatedAt, :payload, 'PENDING', 0, now())
            on conflict (stripe_event_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("stripeEventId") String stripeEventId, @Param("eventType") String eventType,
                       @Param("apiVersion") String apiVersion, @Param("isLivemode") boolean isLivemode,
                       @Param("stripeCreatedAt") Instant stripeCreatedAt, @Param("payload") String payload);

    /**
     * Lock the oldest event that is due, skipping those another instance holds.
     *
     * @param now the current time
     * @param pageable the batch, of one
     * @return the event, if any is due
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // -2 is SKIP LOCKED, so instances running the job at once split the work instead of waiting.
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select e from StripeEvent e
            where e.status = com.triphuddle.api.payment.entity.StripeEventStatus.PENDING
              and e.nextAttemptAt <= :now and e.deletedAt is null
            order by e.stripeCreatedAt asc, e.createdAt asc
            """)
    List<StripeEvent> lockDue(@Param("now") Instant now, Pageable pageable);

    /**
     * Find a page of events for staff, newest first, after a cursor; events arriving meanwhile don't shift the
     * pages.
     *
     * @param status the status, or null for all
     * @param eventType the Stripe event type, or null for all
     * @param isFirst whether this is the first page, which ignores the cursor
     * @param at Stripe's creation time of the previous page's last event
     * @param id its ID
     * @param limit how many to take
     * @return the events
     */
    @Query("""
            select e from StripeEvent e
            where (:status is null or e.status = :status)
              and (:eventType is null or e.eventType = :eventType)
              and e.deletedAt is null
              and (:isFirst = true or e.stripeCreatedAt < :at or (e.stripeCreatedAt = :at and e.id < :id))
            order by e.stripeCreatedAt desc, e.id desc
            """)
    List<StripeEvent> findForStaff(@Param("status") StripeEventStatus status, @Param("eventType") String eventType,
                                   @Param("isFirst") boolean isFirst, @Param("at") Instant at,
                                   @Param("id") UUID id, Pageable limit);
}
