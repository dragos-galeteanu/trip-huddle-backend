package com.triphuddle.api.payment.controller;

import com.triphuddle.api.payment.dto.BillingSubscriptionFilter;
import com.triphuddle.api.payment.dto.BillingSubscriptionResponse;
import com.triphuddle.api.payment.service.BillingSubscriptionService;
import com.triphuddle.common.dto.Paginated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    private final BillingSubscriptionService billingSubscriptionService;

    /**
     * Get billing subscriptions, newest first.
     *
     * @param filter the page and filters
     * @return a page of billing subscriptions
     */
    @GetMapping
    @Operation(summary = "Get billing subscriptions")
    @PreAuthorize("@InvoicePolicy.canGetAll(authentication)")
    public ResponseEntity<Paginated<BillingSubscriptionResponse>> getAll(
            @Valid @ModelAttribute BillingSubscriptionFilter filter) {
        return ResponseEntity.ok(billingSubscriptionService.getAll(filter));
    }
}
