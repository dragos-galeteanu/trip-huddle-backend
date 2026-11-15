package com.triphuddle.api.payment.repository;

import com.triphuddle.api.payment.entity.PaymentRefund;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link PaymentRefund}.
 */
@Repository
public interface PaymentRefundRepository extends BaseJpaRepository<PaymentRefund, UUID> {

    /**
     * Find a refund by its Stripe ID.
     *
     * @param stripeRefundId the Stripe refund ID
     * @return the refund, if recorded
     */
    Optional<PaymentRefund> findByStripeRefundId(String stripeRefundId);

    /**
     * Find an invoice's refunds, oldest first.
     *
     * @param invoiceId the ID of the invoice
     * @return the refunds
     */
    List<PaymentRefund> findByInvoiceIdOrderByStripeCreatedAtAsc(UUID invoiceId);
}
