package com.triphuddle.api.payment.controller;

import com.triphuddle.api.payment.dto.StripeEventFilter;
import com.triphuddle.api.payment.dto.StripeEventResponse;
import com.triphuddle.api.payment.service.StripeEventService;
import com.triphuddle.common.dto.CursorPaginated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Staff endpoints for the received Stripe events under {@code /api/v1/payments/stripe-events}.
 */
@RestController
@RequestMapping("/api/v1/payments/stripe-events")
@RequiredArgsConstructor
@Tag(name = "Stripe events")
public class StripeEventController {

    private final StripeEventService stripeEventService;

    /**
     * Get the received events, newest first.
     *
     * @param filter the cursor, page size and filters
     * @return a page of events, without payloads
     */
    @GetMapping
    @Operation(summary = "Get received Stripe events")
    @PreAuthorize("@StripeEventPolicy.canGetAll(authentication)")
    public ResponseEntity<CursorPaginated<StripeEventResponse>> getAll(
            @Valid @ModelAttribute StripeEventFilter filter) {
        return ResponseEntity.ok(stripeEventService.getAll(filter));
    }

    /**
     * Get a received event with its raw payload.
     *
     * @param id the ID of the stored event
     * @return the event
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a received Stripe event")
    @PreAuthorize("@StripeEventPolicy.canGetById(authentication, #id)")
    public ResponseEntity<StripeEventResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(stripeEventService.getById(id));
    }

    /**
     * Queue a failed or ignored event to be handled again.
     *
     * @param id the ID of the stored event
     * @return the queued event
     */
    @PostMapping("/{id}/replay")
    @Operation(summary = "Replay a received Stripe event")
    @PreAuthorize("@StripeEventPolicy.canReplay(authentication)")
    public ResponseEntity<StripeEventResponse> replay(@PathVariable UUID id) {
        return ResponseEntity.ok(stripeEventService.replay(id));
    }
}
