package com.triphuddle.api.payment.controller;

import com.triphuddle.api.payment.dto.CheckoutRequest;
import com.triphuddle.api.payment.dto.CheckoutResponse;
import com.triphuddle.api.payment.service.CheckoutService;
import com.triphuddle.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Starts Stripe Checkout for a subscription waiting for payment, under {@code /api/v1/payments/checkout}.
 */
@RestController
@RequestMapping("/api/v1/payments/checkout")
@RequiredArgsConstructor
@Tag(name = "Checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;

    /**
     * Get the Stripe Checkout page for a subscription, reusing an open one.
     *
     * @param request the subscription
     * @param authentication the caller
     * @return the page to send the customer to
     */
    @PostMapping
    @Operation(summary = "Start checkout for a pending subscription")
    @PreAuthorize("@CheckoutPolicy.canCreate(authentication, #request)")
    public ResponseEntity<CheckoutResponse> create(@Valid @RequestBody CheckoutRequest request,
                                                   Authentication authentication) {
        return ResponseEntity.ok(checkoutService.checkout(request.getSubscriptionId(),
                SecurityUtils.extractUserId(authentication)));
    }
}
