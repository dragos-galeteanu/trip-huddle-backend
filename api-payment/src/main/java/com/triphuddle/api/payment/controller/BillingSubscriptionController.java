package com.triphuddle.api.payment.controller;

import com.triphuddle.api.payment.dto.BillingSubscriptionFilter;
import com.triphuddle.api.payment.dto.BillingSubscriptionResponse;
import com.triphuddle.api.payment.entity.BillingSubscription;
import com.triphuddle.api.payment.repository.BillingSubscriptionRepository;
import com.triphuddle.common.dto.Paginated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff reads of the billing side of subscriptions, e.g. payments that wait for a refund, under
 * {@code /api/v1/payments/subscriptions}.
 */
@RestController
@RequestMapping("/api/v1/payments/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Billing subscriptions")
public class BillingSubscriptionController {

    private final BillingSubscriptionRepository billingSubscriptionRepository;

    /**
     * Get billing subscriptions, newest first.
     *
     * @param filter the page and filters
     * @return a page of billing subscriptions
     */
    @GetMapping
    @Operation(summary = "Get billing subscriptions")
    @PreAuthorize("@InvoicePolicy.canGetAll(authentication)")
    @Transactional(readOnly = true)
    public ResponseEntity<Paginated<BillingSubscriptionResponse>> getAll(
            @Valid @ModelAttribute BillingSubscriptionFilter filter) {
        return ResponseEntity.ok(Paginated.of(billingSubscriptionRepository.findForStaff(filter.getStatus(),
                filter.getSubscriberId(), filter.getIsRefundDue(), filter.toPageable()),
                subscriptions -> subscriptions.stream().map(BillingSubscriptionController::toResponse).toList()));
    }

    private static BillingSubscriptionResponse toResponse(BillingSubscription subscription) {
        return new BillingSubscriptionResponse(subscription.getLicenseSubscriptionId(), subscription.getStatus(),
                subscription.getLicenseId(), subscription.getSubscriberType(), subscription.getSubscriberId(),
                subscription.getPurchasedByUserId(), subscription.getBillingInterval(),
                subscription.getListPriceMinor(), subscription.getFirstPriceMinor(), subscription.getCurrency(),
                subscription.getStripeCustomerId(), subscription.getStripeSubscriptionId(),
                subscription.isCancelAtPeriodEnd(), subscription.getPaidAt(), subscription.getActivatedAt(),
                subscription.getEndedAt(), subscription.getRefundDueAt());
    }
}
