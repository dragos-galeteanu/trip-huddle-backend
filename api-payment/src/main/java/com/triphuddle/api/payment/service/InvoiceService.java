package com.triphuddle.api.payment.service;

import com.triphuddle.common.filter.CursorPaginationFilter;
import com.triphuddle.common.dto.PageCursor;
import com.triphuddle.common.dto.CursorPaginated;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.payment.dto.InvoiceFilter;
import com.triphuddle.api.payment.dto.InvoiceResponse;
import com.triphuddle.api.payment.dto.RefundRequest;
import com.triphuddle.api.payment.dto.RefundResponse;
import com.triphuddle.api.payment.entity.BillingSubscription;
import com.triphuddle.api.payment.entity.BillingSubscriptionStatus;
import com.triphuddle.api.payment.entity.PaymentInvoice;
import com.triphuddle.api.payment.entity.PaymentRefund;
import com.triphuddle.api.payment.repository.BillingSubscriptionRepository;
import com.triphuddle.api.payment.repository.PaymentInvoiceRepository;
import com.triphuddle.api.payment.repository.PaymentRefundRepository;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.stripe.invoice.StripeInvoiceClient;
import com.triphuddle.stripe.refund.StripeRefund;
import com.triphuddle.stripe.refund.StripeRefundClient;
import com.triphuddle.stripe.subscription.StripeSubscriptionClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Records the invoices of license subscriptions and their refunds, lists them, and refunds them for staff.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceService {

    static final String INVOICE_ID_METADATA = "invoiceId";

    private final PaymentInvoiceRepository paymentInvoiceRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final BillingSubscriptionRepository billingSubscriptionRepository;
    private final StripeInvoiceClient stripeInvoiceClient;
    private final StripeRefundClient stripeRefundClient;
    private final StripeSubscriptionClient stripeSubscriptionClient;

    /**
     * Record an invoice as Stripe reports it now, for the billing subscription it bills.
     *
     * @param invoice the invoice
     * @param subscription the billing subscription
     */
    public void record(StripeInvoice invoice, BillingSubscription subscription) {
        PaymentInvoice recorded = paymentInvoiceRepository.lockByStripeInvoiceId(invoice.id()).orElseGet(() -> {
            PaymentInvoice created = new PaymentInvoice();
            created.setStripeInvoiceId(invoice.id());
            created.setLicenseSubscriptionId(subscription.getLicenseSubscriptionId());
            created.setLicenseId(subscription.getLicenseId());
            created.setSubscriberType(subscription.getSubscriberType());
            created.setSubscriberId(subscription.getSubscriberId());
            created.setStripeSubscriptionId(invoice.stripeSubscriptionId());
            return created;
        });
        recorded.setNumber(invoice.number());
        recorded.setBillingReason(invoice.billingReason());
        recorded.setStatus(invoice.status());
        recorded.setCurrency(invoice.currency().toUpperCase(Locale.ROOT));
        recorded.setTotalMinor(invoice.totalMinor());
        recorded.setTaxMinor(invoice.taxMinor());
        recorded.setAmountPaidMinor(invoice.amountMinor());
        recorded.setHostedInvoiceUrl(invoice.hostedInvoiceUrl());
        recorded.setInvoicePdfUrl(invoice.invoicePdfUrl());
        recorded.setStripeCreatedAt(invoice.createdAt());
        recorded.setPaidAt(invoice.paidAt());
        // Refunds go against the payment intent; a dashboard refund is matched to the invoice by it too.
        if (recorded.getStripePaymentIntentId() == null && invoice.amountMinor() > 0) {
            stripeInvoiceClient.paidPaymentIntentId(invoice.id()).ifPresent(recorded::setStripePaymentIntentId);
        }
        paymentInvoiceRepository.save(recorded);
        log.info("Invoice recorded [invoiceId={}, licenseSubscriptionId={}, status={}]", invoice.id(),
                subscription.getLicenseSubscriptionId(), invoice.status());
    }

    /**
     * Record a refund as Stripe reports it, whoever made it, and update the invoice's refunded amount.
     *
     * @param refund the refund
     */
    public void onRefund(StripeRefund refund) {
        Optional<PaymentInvoice> invoice = invoiceOf(refund);
        if (invoice.isEmpty()) {
            log.info("Refund of a payment that is not a recorded invoice skipped [refundId={}, paymentIntentId={}]",
                    refund.id(), refund.paymentIntentId());
            return;
        }
        book(invoice.get(), refund, null);
    }

    /**
     * Refund part or all of an invoice's payment, and optionally stop its Stripe subscription at once.
     *
     * @param invoiceId the ID of the invoice
     * @param request the amount, or null for all that is left, and whether to end the subscription
     * @param actorId who refunds it
     * @return the refund
     */
    public RefundResponse refund(UUID invoiceId, RefundRequest request, UUID actorId) {
        PaymentInvoice invoice = paymentInvoiceRepository.lockById(invoiceId).orElseThrow(() -> notFound(invoiceId));
        long refundable = invoice.refundableMinor();
        long amount = request.getAmountMinor() == null ? refundable : request.getAmountMinor();
        if (invoice.getStripePaymentIntentId() == null || refundable == 0) {
            throw new IllegalStateException("Invoice with id=%s has nothing to refund".formatted(invoiceId));
        }
        if (amount <= 0 || amount > refundable) {
            throw new IllegalStateException("Refund must be between 1 and %d".formatted(refundable));
        }
        // The same request twice, e.g. a double click, makes one refund: the key covers what was refunded so far.
        String idempotencyKey = "refund-%s-%d-%d".formatted(invoiceId, invoice.getAmountRefundedMinor(), amount);
        StripeRefund refund = stripeRefundClient.create(invoice.getStripePaymentIntentId(), amount,
                Boolean.TRUE.equals(request.getIsRequestedByCustomer()),
                Map.of(INVOICE_ID_METADATA, invoiceId.toString(), "createdByUserId", String.valueOf(actorId)),
                idempotencyKey);
        PaymentRefund booked = book(invoice, refund, actorId);

        if (Boolean.TRUE.equals(request.getIsEndingSubscription())) {
            endSubscription(invoice);
        }
        log.info("Invoice refunded [invoiceId={}, refundId={}, amountMinor={}, actorId={}]", invoiceId, refund.id(),
                amount, actorId);
        return toRefundResponse(booked);
    }

    /**
     * Get the invoices of the caller's own subscriptions.
     *
     * @param userId the ID of the user
     * @param filter the cursor and page size
     * @return a page of invoices
     */
    @Transactional(readOnly = true)
    public CursorPaginated<InvoiceResponse> getMine(UUID userId, CursorPaginationFilter filter) {
        return forSubscriber(LicenseTarget.USER, userId, filter);
    }

    /**
     * Get the invoices of a group's subscriptions.
     *
     * @param groupId the ID of the group
     * @param filter the cursor and page size
     * @return a page of invoices
     */
    @Transactional(readOnly = true)
    public CursorPaginated<InvoiceResponse> getForGroup(UUID groupId, CursorPaginationFilter filter) {
        return forSubscriber(LicenseTarget.GROUP, groupId, filter);
    }

    private CursorPaginated<InvoiceResponse> forSubscriber(LicenseTarget subscriberType, UUID subscriberId,
                                                           CursorPaginationFilter filter) {
        PageCursor cursor = PageCursor.decode(filter.getCursor(), 2);
        boolean isFirst = cursor == null;
        List<PaymentInvoice> found = paymentInvoiceRepository.findForSubscriber(subscriberType, subscriberId,
                isFirst, isFirst ? PageCursor.FIRST_INSTANT : cursor.instantAt(0),
                isFirst ? PageCursor.FIRST_ID : cursor.uuidAt(1), filter.toLimit());
        return CursorPaginated.of(found, filter.getLimit(), invoice -> PageCursor.of(invoice.getStripeCreatedAt(),
                invoice.getId()), this::toResponses);
    }

    /**
     * Get invoices for staff.
     *
     * @param filter the page and filters
     * @return a page of invoices
     */
    @Transactional(readOnly = true)
    public Paginated<InvoiceResponse> getAll(InvoiceFilter filter) {
        String status = filter.getStatus() == null || filter.getStatus().isBlank()
                ? null : filter.getStatus().trim().toLowerCase(Locale.ROOT);
        return Paginated.of(paymentInvoiceRepository.findForStaff(filter.getSubscriberId(),
                filter.getLicenseSubscriptionId(), status, filter.toPageable()), this::toResponses);
    }

    /**
     * Get an invoice with its refunds.
     *
     * @param id the ID of the invoice
     * @return the invoice
     */
    @Transactional(readOnly = true)
    public InvoiceResponse getById(UUID id) {
        return toResponse(paymentInvoiceRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> notFound(id)));
    }

    private Optional<PaymentInvoice> invoiceOf(StripeRefund refund) {
        String invoiceId = refund.metadata().get(INVOICE_ID_METADATA);
        if (invoiceId != null) {
            Optional<PaymentInvoice> found = paymentInvoiceRepository.lockById(UUID.fromString(invoiceId));
            if (found.isPresent()) {
                return found;
            }
        }
        return refund.paymentIntentId() == null ? Optional.empty()
                : paymentInvoiceRepository.lockByStripePaymentIntentId(refund.paymentIntentId());
    }

    // Upserts the refund by its Stripe ID, as the API answer and its events both report it, and recounts the
    // invoice's refunded amount from every refund that went or is going through.
    private PaymentRefund book(PaymentInvoice invoice, StripeRefund refund, UUID actorId) {
        PaymentRefund booked = paymentRefundRepository.findByStripeRefundId(refund.id()).orElseGet(() -> {
            PaymentRefund created = new PaymentRefund();
            created.setInvoice(invoice);
            created.setStripeRefundId(refund.id());
            created.setCreatedByUserId(actorId);
            return created;
        });
        booked.setAmountMinor(refund.amountMinor());
        booked.setCurrency(refund.currency().toUpperCase(Locale.ROOT));
        booked.setStatus(refund.status());
        booked.setReason(refund.reason());
        booked.setStripeCreatedAt(refund.createdAt());
        paymentRefundRepository.saveAndFlush(booked);

        long refunded = paymentRefundRepository.findByInvoiceIdOrderByStripeCreatedAtAsc(invoice.getId()).stream()
                .filter(PaymentRefund::isCounted)
                .mapToLong(PaymentRefund::getAmountMinor)
                .sum();
        invoice.setAmountRefundedMinor(refunded);
        // A payment flagged for refund is settled once anything of it is given back.
        billingSubscriptionRepository.lockByLicenseSubscriptionId(invoice.getLicenseSubscriptionId())
                .filter(subscription -> subscription.getRefundDueAt() != null && booked.isCounted())
                .ifPresent(subscription -> subscription.setRefundDueAt(null));
        log.info("Refund recorded [refundId={}, invoiceId={}, status={}, refundedMinor={}]", refund.id(),
                invoice.getId(), refund.status(), refunded);
        return booked;
    }

    // Stops the Stripe subscription now; Stripe's customer.subscription.deleted then expires the subscription.
    private void endSubscription(PaymentInvoice invoice) {
        billingSubscriptionRepository.lockByLicenseSubscriptionId(invoice.getLicenseSubscriptionId())
                .filter(subscription -> subscription.getStatus() == BillingSubscriptionStatus.ACTIVE
                        && invoice.getStripeSubscriptionId().equals(subscription.getStripeSubscriptionId()))
                .ifPresent(subscription -> {
                    stripeSubscriptionClient.cancelNow(subscription.getStripeSubscriptionId());
                    log.info("Subscription ended with its refund [licenseSubscriptionId={}]",
                            subscription.getLicenseSubscriptionId());
                });
    }

    private List<InvoiceResponse> toResponses(List<PaymentInvoice> invoices) {
        return invoices.stream().map(invoice -> toResponse(invoice, List.of())).toList();
    }

    private InvoiceResponse toResponse(PaymentInvoice invoice) {
        return toResponse(invoice, paymentRefundRepository.findByInvoiceIdOrderByStripeCreatedAtAsc(invoice.getId())
                .stream().map(InvoiceService::toRefundResponse).toList());
    }

    private static InvoiceResponse toResponse(PaymentInvoice invoice, List<RefundResponse> refunds) {
        return new InvoiceResponse(invoice.getId(), invoice.getNumber(), invoice.getLicenseSubscriptionId(),
                invoice.getLicenseId(), invoice.getSubscriberType(), invoice.getSubscriberId(),
                invoice.getBillingReason(), invoice.getStatus(), invoice.getCurrency(), invoice.getTotalMinor(),
                invoice.getTaxMinor(), invoice.getAmountPaidMinor(), invoice.getAmountRefundedMinor(),
                invoice.getHostedInvoiceUrl(), invoice.getInvoicePdfUrl(), invoice.getStripeCreatedAt(),
                invoice.getPaidAt(), refunds);
    }

    private static RefundResponse toRefundResponse(PaymentRefund refund) {
        return new RefundResponse(refund.getId(), refund.getStripeRefundId(), refund.getAmountMinor(),
                refund.getCurrency(), refund.getStatus(), refund.getReason(), refund.getCreatedByUserId(),
                refund.getStripeCreatedAt());
    }

    private static ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Invoice with id=%s not found".formatted(id));
    }
}
