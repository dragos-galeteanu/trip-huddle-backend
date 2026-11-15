package com.triphuddle.api.payment.repository;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.payment.entity.PaymentInvoice;
import com.triphuddle.common.repository.BaseJpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link PaymentInvoice}.
 */
@Repository
public interface PaymentInvoiceRepository extends BaseJpaRepository<PaymentInvoice, UUID> {

    /**
     * Lock an invoice by its Stripe ID, so its events and refunds change it one at a time.
     *
     * @param stripeInvoiceId the Stripe invoice ID
     * @return the locked invoice, if recorded
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PaymentInvoice i where i.stripeInvoiceId = :stripeInvoiceId")
    Optional<PaymentInvoice> lockByStripeInvoiceId(@Param("stripeInvoiceId") String stripeInvoiceId);

    /**
     * Lock an invoice by its ID.
     *
     * @param id the ID of the invoice
     * @return the locked invoice
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PaymentInvoice i where i.id = :id and i.deletedAt is null")
    Optional<PaymentInvoice> lockById(@Param("id") UUID id);

    /**
     * Lock the invoice a payment intent paid, so a refund from it can be booked against it.
     *
     * @param paymentIntentId the Stripe payment intent ID
     * @return the locked invoice, if recorded
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PaymentInvoice i where i.stripePaymentIntentId = :paymentIntentId")
    Optional<PaymentInvoice> lockByStripePaymentIntentId(@Param("paymentIntentId") String paymentIntentId);

    /**
     * Find a subscriber's invoices, newest first.
     *
     * @param subscriberType USER or GROUP
     * @param subscriberId the ID of the user or group
     * @param pageable the page
     * @return a page of invoices
     */
    @Query("""
            select i from PaymentInvoice i
            where i.subscriberType = :subscriberType and i.subscriberId = :subscriberId and i.deletedAt is null
            order by i.stripeCreatedAt desc
            """)
    Page<PaymentInvoice> findForSubscriber(@Param("subscriberType") LicenseTarget subscriberType,
                                           @Param("subscriberId") UUID subscriberId, Pageable pageable);

    /**
     * Find invoices for staff, newest first.
     *
     * @param subscriberId the user or group, or null for all
     * @param licenseSubscriptionId the subscription, or null for all
     * @param status Stripe's status, or null for all
     * @param pageable the page
     * @return a page of invoices
     */
    @Query("""
            select i from PaymentInvoice i
            where (:subscriberId is null or i.subscriberId = :subscriberId)
              and (:licenseSubscriptionId is null or i.licenseSubscriptionId = :licenseSubscriptionId)
              and (:status is null or i.status = :status)
              and i.deletedAt is null
            order by i.stripeCreatedAt desc
            """)
    Page<PaymentInvoice> findForStaff(@Param("subscriberId") UUID subscriberId,
                                      @Param("licenseSubscriptionId") UUID licenseSubscriptionId,
                                      @Param("status") String status, Pageable pageable);
}
