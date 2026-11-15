package com.triphuddle.api.payment.controller;

import com.triphuddle.api.payment.dto.InvoiceFilter;
import com.triphuddle.api.payment.dto.InvoiceResponse;
import com.triphuddle.api.payment.dto.RefundRequest;
import com.triphuddle.api.payment.dto.RefundResponse;
import com.triphuddle.api.payment.service.InvoiceService;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Invoices of license subscriptions and their refunds, under {@code /api/v1/payments/invoices}.
 */
@RestController
@RequestMapping("/api/v1/payments/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    /**
     * Get the invoices of the caller's own subscriptions, newest first.
     *
     * @param filter the page
     * @param authentication the caller
     * @return a page of invoices
     */
    @GetMapping("/mine")
    @Operation(summary = "Get my invoices")
    @PreAuthorize("@InvoicePolicy.canGetMine(authentication)")
    public ResponseEntity<Paginated<InvoiceResponse>> getMine(@Valid @ModelAttribute PaginationFilter filter,
                                                              Authentication authentication) {
        return ResponseEntity.ok(invoiceService.getMine(SecurityUtils.extractUserId(authentication), filter));
    }

    /**
     * Get the invoices of a group's subscriptions, newest first.
     *
     * @param groupId the ID of the group
     * @param filter the page
     * @return a page of invoices
     */
    @GetMapping("/groups/{groupId}")
    @Operation(summary = "Get a group's invoices")
    @PreAuthorize("@InvoicePolicy.canGetForGroup(authentication, #groupId)")
    public ResponseEntity<Paginated<InvoiceResponse>> getForGroup(@PathVariable UUID groupId,
                                                                  @Valid @ModelAttribute PaginationFilter filter) {
        return ResponseEntity.ok(invoiceService.getForGroup(groupId, filter));
    }

    /**
     * Get invoices for staff, newest first.
     *
     * @param filter the page and filters
     * @return a page of invoices
     */
    @GetMapping
    @Operation(summary = "Get invoices")
    @PreAuthorize("@InvoicePolicy.canGetAll(authentication)")
    public ResponseEntity<Paginated<InvoiceResponse>> getAll(@Valid @ModelAttribute InvoiceFilter filter) {
        return ResponseEntity.ok(invoiceService.getAll(filter));
    }

    /**
     * Get an invoice with its refunds.
     *
     * @param id the ID of the invoice
     * @return the invoice
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get an invoice")
    @PreAuthorize("@InvoicePolicy.canGetById(authentication, #id)")
    public ResponseEntity<InvoiceResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.getById(id));
    }

    /**
     * Refund part or all of an invoice's payment.
     *
     * @param id the ID of the invoice
     * @param request the amount and whether its subscription ends
     * @param authentication the caller
     * @return the refund
     */
    @PostMapping("/{id}/refund")
    @Operation(summary = "Refund an invoice")
    @PreAuthorize("@InvoicePolicy.canRefund(authentication)")
    public ResponseEntity<RefundResponse> refund(@PathVariable UUID id, @Valid @RequestBody RefundRequest request,
                                                 Authentication authentication) {
        return new ResponseEntity<>(invoiceService.refund(id, request, SecurityUtils.extractUserId(authentication)),
                HttpStatus.CREATED);
    }
}
