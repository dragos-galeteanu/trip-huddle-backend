package com.triphuddle.api.license.controller;

import com.triphuddle.api.license.dto.PromotionRequest;
import com.triphuddle.api.license.dto.PromotionResponse;
import com.triphuddle.api.license.service.PromotionService;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.filter.PaginationFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST endpoints for a license's promotions under {@code /api/v1/licenses/{licenseId}/promotions}; staff only,
 * as codes are not public.
 */
@RestController
@RequestMapping("/api/v1/licenses/{licenseId}/promotions")
@RequiredArgsConstructor
@Tag(name = "Promotions")
public class PromotionController {

    private final PromotionService promotionService;

    /**
     * Create a promotion.
     *
     * @param licenseId the ID of the license
     * @param request the promotion
     * @return the created promotion
     */
    @PostMapping
    @Operation(summary = "Create a promotion")
    @PreAuthorize("@LicensePolicy.canManage(authentication)")
    public ResponseEntity<PromotionResponse> create(@PathVariable UUID licenseId,
                                                    @Valid @RequestBody PromotionRequest request) {
        return new ResponseEntity<>(promotionService.create(licenseId, request), HttpStatus.CREATED);
    }

    /**
     * Get the promotions of a license, latest start first.
     *
     * @param licenseId the ID of the license
     * @param filter pagination parameters
     * @return a page of promotions
     */
    @GetMapping
    @Operation(summary = "Get a license's promotions")
    @PreAuthorize("@LicensePolicy.canReadPromotions(authentication)")
    public ResponseEntity<Paginated<PromotionResponse>> getAll(@PathVariable UUID licenseId,
                                                               @Valid @ModelAttribute PaginationFilter filter) {
        return ResponseEntity.ok(promotionService.getAll(licenseId, filter));
    }

    /**
     * Get a promotion.
     *
     * @param licenseId the ID of the license
     * @param id the ID of the promotion
     * @return the promotion
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a promotion")
    @PreAuthorize("@LicensePolicy.canReadPromotions(authentication)")
    public ResponseEntity<PromotionResponse> getById(@PathVariable UUID licenseId, @PathVariable UUID id) {
        return ResponseEntity.ok(promotionService.getById(licenseId, id));
    }

    /**
     * Update a promotion.
     *
     * @param licenseId the ID of the license
     * @param id the ID of the promotion
     * @param request the new promotion
     * @return the updated promotion
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update a promotion")
    @PreAuthorize("@LicensePolicy.canManage(authentication)")
    public ResponseEntity<PromotionResponse> update(@PathVariable UUID licenseId, @PathVariable UUID id,
                                                    @Valid @RequestBody PromotionRequest request) {
        return ResponseEntity.ok(promotionService.update(licenseId, id, request));
    }

    /**
     * End a running promotion now.
     *
     * @param licenseId the ID of the license
     * @param id the ID of the promotion
     * @return the ended promotion
     */
    @PostMapping("/{id}/end")
    @Operation(summary = "End a promotion")
    @PreAuthorize("@LicensePolicy.canManage(authentication)")
    public ResponseEntity<PromotionResponse> end(@PathVariable UUID licenseId, @PathVariable UUID id) {
        return ResponseEntity.ok(promotionService.end(licenseId, id));
    }

    /**
     * Delete a promotion that was never redeemed.
     *
     * @param licenseId the ID of the license
     * @param id the ID of the promotion
     * @return the deleted promotion
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a promotion")
    @PreAuthorize("@LicensePolicy.canManage(authentication)")
    public ResponseEntity<PromotionResponse> delete(@PathVariable UUID licenseId, @PathVariable UUID id) {
        return ResponseEntity.ok(promotionService.delete(licenseId, id));
    }
}
