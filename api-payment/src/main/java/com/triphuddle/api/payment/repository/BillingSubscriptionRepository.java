package com.triphuddle.api.payment.repository;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.payment.entity.BillingSubscription;
import com.triphuddle.api.payment.entity.BillingSubscriptionStatus;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link BillingSubscription}.
 */
@Repository
public interface BillingSubscriptionRepository extends BaseJpaRepository<BillingSubscription, UUID> {

    /**
     * Find the billing side of a license subscription.
     *
     * @param licenseSubscriptionId the ID of the subscription in api-license
     * @return the billing subscription, if any event about it arrived
     */
    Optional<BillingSubscription> findByLicenseSubscriptionId(UUID licenseSubscriptionId);

    /**
     * Lock the billing side of a license subscription, so checkout and its events change it one at a time.
     *
     * @param licenseSubscriptionId the ID of the subscription in api-license
     * @return the locked billing subscription
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from BillingSubscription s where s.licenseSubscriptionId = :licenseSubscriptionId")
    Optional<BillingSubscription> lockByLicenseSubscriptionId(
            @Param("licenseSubscriptionId") UUID licenseSubscriptionId);

    /**
     * Lock the billing subscription that a Stripe subscription currently bills.
     *
     * @param stripeSubscriptionId the Stripe subscription ID
     * @return the locked billing subscription, if one holds it
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from BillingSubscription s where s.stripeSubscriptionId = :stripeSubscriptionId")
    Optional<BillingSubscription> lockByStripeSubscriptionId(
            @Param("stripeSubscriptionId") String stripeSubscriptionId);

    /**
     * Find a subscriber's billing subscriptions in a status that a Stripe subscription bills.
     *
     * @param subscriberType USER or GROUP
     * @param subscriberId the ID of the user or group
     * @param status the status
     * @return the billing subscriptions
     */
    List<BillingSubscription> findBySubscriberTypeAndSubscriberIdAndStatusAndStripeSubscriptionIdIsNotNull(
            LicenseTarget subscriberType, UUID subscriberId, BillingSubscriptionStatus status);

    /**
     * Find billing subscriptions for staff, newest first.
     *
     * @param status the status, or null for all
     * @param subscriberId the user or group, or null for all
     * @param isRefundDue true for only those waiting for a refund, false for the others, null for all
     * @param pageable the page
     * @return a page of billing subscriptions
     */
    @Query("""
            select s from BillingSubscription s
            where (:status is null or s.status = :status)
              and (:subscriberId is null or s.subscriberId = :subscriberId)
              and (:isRefundDue is null or (:isRefundDue = true and s.refundDueAt is not null)
                   or (:isRefundDue = false and s.refundDueAt is null))
              and s.deletedAt is null
            order by s.createdAt desc
            """)
    Page<BillingSubscription> findForStaff(@Param("status") BillingSubscriptionStatus status,
                                           @Param("subscriberId") UUID subscriberId,
                                           @Param("isRefundDue") Boolean isRefundDue, Pageable pageable);

    /**
     * Lock the billing subscription Stripe holds something for that was compared with it longest ago, before a
     * cutoff, skipping those another instance holds.
     *
     * @param cutoff the latest comparison that counts as overdue
     * @param pageable the batch, of one
     * @return the billing subscription, if any is due
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // -2 is SKIP LOCKED, so instances running the job at once split the work instead of waiting.
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select s from BillingSubscription s
            where ((s.status = com.triphuddle.api.payment.entity.BillingSubscriptionStatus.ACTIVE
                    and s.stripeSubscriptionId is not null and s.refundDueAt is null)
                or (s.status = com.triphuddle.api.payment.entity.BillingSubscriptionStatus.PENDING
                    and s.stripeCheckoutSessionId is not null))
              and (s.reconciledAt is null or s.reconciledAt < :cutoff)
              and s.deletedAt is null
            order by s.reconciledAt asc nulls first
            """)
    List<BillingSubscription> lockDueForReconciliation(@Param("cutoff") Instant cutoff, Pageable pageable);
}
