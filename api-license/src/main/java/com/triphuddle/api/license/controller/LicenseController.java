package com.triphuddle.api.license.controller;

import com.triphuddle.api.license.dto.CreateLicenseRequest;
import com.triphuddle.api.license.dto.LicenseFilter;
import com.triphuddle.api.license.dto.LicenseResponse;
import com.triphuddle.api.license.dto.PriceQuoteResponse;
import com.triphuddle.api.license.dto.ReplaceEntitlementsRequest;
import com.triphuddle.api.license.dto.UpdateLicenseRequest;
import com.triphuddle.api.license.security.LicenseAuthorizationPolicy;
import com.triphuddle.api.license.service.LicenseService;
import com.triphuddle.api.license.service.PricingService;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST endpoints for licenses under {@code /api/v1/licenses}; the catalog reads are public.
 */
@RestController
@RequestMapping("/api/v1/licenses")
@RequiredArgsConstructor
@Tag(name = "Licenses")
public class LicenseController {

    private final LicenseService licenseService;
    private final PricingService pricingService;
    private final LicenseAuthorizationPolicy licensePolicy;

    /**
     * Create a license as a draft.
     *
     * @param request the license
     * @return the created license
     */
    @PostMapping
    @Operation(summary = "Create a license")
    @PreAuthorize("@LicensePolicy.canCreate(authentication, #request)")
    public ResponseEntity<LicenseResponse> create(@Valid @RequestBody CreateLicenseRequest request) {
        return new ResponseEntity<>(licenseService.create(request), HttpStatus.CREATED);
    }

    /**
     * Get licenses, cheapest first; only staff see licenses that are not published.
     *
     * @param filter the page and the filters
     * @param authentication the caller, possibly anonymous
     * @return a page of licenses
     */
    @GetMapping
    @Operation(summary = "Get licenses")
    @PreAuthorize("@LicensePolicy.canGetAll(authentication)")
    public ResponseEntity<Paginated<LicenseResponse>> getAll(@Valid @ModelAttribute LicenseFilter filter,
                                                             Authentication authentication) {
        return ResponseEntity.ok(licenseService.getAll(filter, licensePolicy.isAdminOrSupportOrInspector(authentication)));
    }

    /**
     * Get a license; only staff find licenses that are not published.
     *
     * @param id the ID of the license
     * @param authentication the caller, possibly anonymous
     * @return the license
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a license")
    @PreAuthorize("@LicensePolicy.canGetById(authentication, #id)")
    public ResponseEntity<LicenseResponse> getById(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(licenseService.getById(id, licensePolicy.isAdminOrSupportOrInspector(authentication)));
    }

    /**
     * Quote one period of a license now, with the promotion a code unlocks or else the automatic one;
     * a signed-in caller's own promotion limits apply.
     *
     * @param id the ID of the license
     * @param code the promotion code the customer entered, if any
     * @param groupId for a group license, the group the caller would subscribe, if any
     * @param authentication the caller, possibly anonymous
     * @return the quote
     */
    @GetMapping("/{id}/price")
    @Operation(summary = "Quote a license's price")
    @PreAuthorize("@LicensePolicy.canGetById(authentication, #id)")
    public ResponseEntity<PriceQuoteResponse> quote(@PathVariable UUID id, @RequestParam(required = false) String code,
                                                    @RequestParam(required = false) UUID groupId,
                                                    Authentication authentication) {
        UUID userId = licensePolicy.isAuthenticated(authentication) ? SecurityUtils.extractUserId(authentication) : null;
        boolean isStaff = licensePolicy.isAdminOrSupportOrInspector(authentication);
        return ResponseEntity.ok(pricingService.quote(id, code, isStaff, userId, groupId));
    }

    /**
     * Update a license's details.
     *
     * @param id the ID of the license
     * @param request the new details
     * @return the updated license
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update a license")
    @PreAuthorize("@LicensePolicy.canUpdate(authentication, #request)")
    public ResponseEntity<LicenseResponse> update(@PathVariable UUID id,
                                                  @Valid @RequestBody UpdateLicenseRequest request) {
        return ResponseEntity.ok(licenseService.update(id, request));
    }

    /**
     * Replace every entitlement of a license.
     *
     * @param id the ID of the license
     * @param request the new entitlements
     * @return the updated license
     */
    @PutMapping("/{id}/entitlements")
    @Operation(summary = "Replace a license's entitlements")
    @PreAuthorize("@LicensePolicy.canManage(authentication)")
    public ResponseEntity<LicenseResponse> replaceEntitlements(@PathVariable UUID id,
                                                               @Valid @RequestBody ReplaceEntitlementsRequest request) {
        return ResponseEntity.ok(licenseService.replaceEntitlements(id, request.getEntitlements()));
    }

    /**
     * Publish a draft or archived license.
     *
     * @param id the ID of the license
     * @return the published license
     */
    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish a license")
    @PreAuthorize("@LicensePolicy.canManage(authentication)")
    public ResponseEntity<LicenseResponse> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(licenseService.publish(id));
    }

    /**
     * Archive a published license.
     *
     * @param id the ID of the license
     * @return the archived license
     */
    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive a license")
    @PreAuthorize("@LicensePolicy.canManage(authentication)")
    public ResponseEntity<LicenseResponse> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(licenseService.archive(id));
    }

    /**
     * Delete a draft license.
     *
     * @param id the ID of the license
     * @return the deleted license
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a draft license")
    @PreAuthorize("@LicensePolicy.canDelete(authentication, #id)")
    public ResponseEntity<LicenseResponse> delete(@PathVariable UUID id) {
        return ResponseEntity.ok(licenseService.delete(id));
    }
}
