package com.triphuddle.api.license.repository;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.license.entity.Subscription;
import com.triphuddle.api.license.entity.SubscriptionStatus;
import com.triphuddle.common.repository.BaseJpaRepository;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
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
 * Data access for {@link Subscription}.
 */
@Repository
public interface SubscriptionRepository extends BaseJpaRepository<Subscription, UUID> {

    /**
     * Find a subscriber's subscription in a status; there is at most one ACTIVE and one PENDING_PAYMENT.
     *
     * @param subscriberType whether the subscriber is a user or a group
     * @param subscriberId the ID of the subscriber
     * @param status the status
     * @return the subscription, or empty if there is none
     */
    Optional<Subscription> findFirstBySubscriberTypeAndSubscriberIdAndStatusAndDeletedAtIsNull(
            LicenseTarget subscriberType, UUID subscriberId, SubscriptionStatus status);

    /**
     * Check if a subscriber ever had a subscription that started.
     *
     * @param subscriberType whether the subscriber is a user or a group
     * @param subscriberId the ID of the subscriber
     * @return true if one of its subscriptions was ever activated
     */
    boolean existsBySubscriberTypeAndSubscriberIdAndActivatedAtIsNotNull(LicenseTarget subscriberType,
                                                                         UUID subscriberId);

    /**
     * Find a subscriber's subscriptions, newest first.
     *
     * @param subscriberType whether the subscriber is a user or a group
     * @param subscriberId the ID of the subscriber
     * @param status the status to keep, or null for all
     * @param pageable the page to return
     * @return a page of subscriptions
     */
    @Query(value = """
            select s from Subscription s
            where s.subscriberType = :subscriberType and s.subscriberId = :subscriberId
              and s.deletedAt is null
              and (:status is null or s.status = :status)
            order by s.createdAt desc
            """,
            countQuery = """
            select count(s) from Subscription s
            where s.subscriberType = :subscriberType and s.subscriberId = :subscriberId
              and s.deletedAt is null
              and (:status is null or s.status = :status)
            """)
    Page<Subscription> findForSubscriber(@Param("subscriberType") LicenseTarget subscriberType,
                                         @Param("subscriberId") UUID subscriberId,
                                         @Param("status") SubscriptionStatus status,
                                         Pageable pageable);

    /**
     * Find subscriptions for staff, newest first; a missing filter keeps every subscription.
     *
     * @param subscriberId the ID of the subscriber to keep, or null for all
     * @param licenseId the ID of the license to keep, or null for all
     * @param status the status to keep, or null for all
     * @param pageable the page to return
     * @return a page of subscriptions
     */
    @Query(value = """
            select s from Subscription s
            where s.deletedAt is null
              and (:subscriberId is null or s.subscriberId = :subscriberId)
              and (:licenseId is null or s.license.id = :licenseId)
              and (:status is null or s.status = :status)
            order by s.createdAt desc
            """,
            countQuery = """
            select count(s) from Subscription s
            where s.deletedAt is null
              and (:subscriberId is null or s.subscriberId = :subscriberId)
              and (:licenseId is null or s.license.id = :licenseId)
              and (:status is null or s.status = :status)
            """)
    Page<Subscription> findForStaff(@Param("subscriberId") UUID subscriberId,
                                    @Param("licenseId") UUID licenseId,
                                    @Param("status") SubscriptionStatus status,
                                    Pageable pageable);

    /**
     * Find a subscription that is not deleted and lock it, so a change and the jobs don't cross.
     *
     * @param id the ID of the subscription
     * @return the subscription, or empty if it is missing or deleted
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subscription s where s.id = :id and s.deletedAt is null")
    Optional<Subscription> lockById(@Param("id") UUID id);

    /**
     * Lock active subscriptions whose period is over, oldest end first, skipping those another instance holds.
     * Those Stripe bills are left out: Stripe renews and ends them.
     *
     * @param now the current moment
     * @param pageable the batch to lock
     * @return the locked subscriptions
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // -2 is SKIP LOCKED, so instances running the job at once split the work instead of waiting.
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select s from Subscription s
            where s.status = com.triphuddle.api.license.entity.SubscriptionStatus.ACTIVE
              and s.currentPeriodEnd <= :now and s.isBilledByStripe = false and s.deletedAt is null
            order by s.currentPeriodEnd asc
            """)
    List<Subscription> lockDueForRenewal(@Param("now") Instant now, Pageable pageable);

    /**
     * Lock subscriptions still waiting for payment since before a cutoff, skipping those another instance holds.
     *
     * @param cutoff the moment they must have been created before
     * @param pageable the batch to lock
     * @return the locked subscriptions
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select s from Subscription s
            where s.status = com.triphuddle.api.license.entity.SubscriptionStatus.PENDING_PAYMENT
              and s.createdAt <= :cutoff and s.deletedAt is null
            order by s.createdAt asc
            """)
    List<Subscription> lockPendingCreatedBefore(@Param("cutoff") Instant cutoff, Pageable pageable);

    /**
     * Find a subscriber's subscriptions in the given statuses.
     *
     * @param subscriberType whether the subscriber is a user or a group
     * @param subscriberId the ID of the subscriber
     * @param statuses the statuses to keep
     * @return the subscriptions
     */
    List<Subscription> findBySubscriberTypeAndSubscriberIdAndStatusInAndDeletedAtIsNull(
            LicenseTarget subscriberType, UUID subscriberId, Collection<SubscriptionStatus> statuses);

    /**
     * Lock auto-renewing subscriptions that renew before a moment and were not reminded of that renewal,
     * skipping those another instance holds.
     *
     * @param now the current moment
     * @param until the latest renewal to remind of
     * @param pageable the batch to lock
     * @return the locked subscriptions
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select s from Subscription s
            where s.status = com.triphuddle.api.license.entity.SubscriptionStatus.ACTIVE
              and s.isAutoRenew = true and s.isGranted = false and s.deletedAt is null
              and s.currentPeriodEnd > :now and s.currentPeriodEnd <= :until
              and (s.remindedPeriodEnd is null or s.remindedPeriodEnd <> s.currentPeriodEnd)
            order by s.currentPeriodEnd asc
            """)
    List<Subscription> lockDueForReminder(@Param("now") Instant now, @Param("until") Instant until,
                                          Pageable pageable);
}
