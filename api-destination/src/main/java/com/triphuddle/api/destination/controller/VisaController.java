package com.triphuddle.api.destination.controller;

import com.triphuddle.api.destination.dto.TripVisaResponse;
import com.triphuddle.api.destination.dto.VisaCheckResponse;
import com.triphuddle.api.destination.dto.VisaRuleImportResponse;
import com.triphuddle.api.destination.security.DestinationAuthorizationPolicy;
import com.triphuddle.api.destination.visa.VisaGuidanceService;
import com.triphuddle.api.destination.visa.VisaRuleImportService;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Visa checks for trips and passports, and the rules behind them, under {@code /api/v1/destinations}.
 */
@RestController
@RequestMapping("/api/v1/destinations")
@RequiredArgsConstructor
@Tag(name = "Visas")
public class VisaController {

    private final VisaGuidanceService guidanceService;
    private final VisaRuleImportService importService;
    private final DestinationAuthorizationPolicy destinationPolicy;

    /**
     * Get what a trip's travellers need to enter its countries: everyone for a group's admins, else the caller.
     *
     * @param planId the ID of the plan
     * @param language the language; without it, the Accept-Language header, else the plan's
     * @param acceptLanguage the Accept-Language header
     * @param authentication the caller
     * @return the checks; guidance still being written comes back GENERATING, so ask again
     */
    @GetMapping("/trip-plans/{planId}/visas")
    @Operation(summary = "Get a trip's visa checks")
    @PreAuthorize("@TripPolicy.canGetById(authentication, #planId)")
    public ResponseEntity<TripVisaResponse> forPlan(
            @PathVariable UUID planId,
            @RequestParam(required = false) String language,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
            Authentication authentication) {
        return ResponseEntity.ok(guidanceService.forPlan(planId, SecurityUtils.extractUserId(authentication),
                destinationPolicy.isStaff(authentication), language != null ? language : acceptLanguage));
    }

    /**
     * Get what one passport needs to enter a country.
     *
     * @param passport the passport's country, ISO 3166-1 alpha-2
     * @param destination the destination, ISO 3166-1 alpha-2
     * @param language the language; without it, the Accept-Language header, else English
     * @param acceptLanguage the Accept-Language header
     * @param groupId the group the caller acts for, or null
     * @param authentication the caller
     * @return the check
     */
    @GetMapping("/visas")
    @Operation(summary = "Check a passport for a country")
    @PreAuthorize("@DestinationPolicy.canBrowse(authentication, #groupId)")
    public ResponseEntity<VisaCheckResponse> check(
            @RequestParam String passport,
            @RequestParam String destination,
            @RequestParam(required = false) String language,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
            @RequestParam(required = false) UUID groupId,
            Authentication authentication) {
        return ResponseEntity.ok(guidanceService.check(passport, destination,
                language != null ? language : acceptLanguage, SecurityUtils.extractUserId(authentication)));
    }

    /**
     * Load the visa rules from the dataset again.
     *
     * @param authentication the caller
     * @return the import, QUEUED
     */
    @PostMapping("/visa-rules/imports")
    @Operation(summary = "Import the visa rules")
    @PreAuthorize("@DestinationPolicy.canManage(authentication)")
    public ResponseEntity<VisaRuleImportResponse> importRules(Authentication authentication) {
        return new ResponseEntity<>(importService.queue(SecurityUtils.extractUserId(authentication)),
                HttpStatus.ACCEPTED);
    }

    /**
     * Get the visa rule imports, the newest first.
     *
     * @param filter the page
     * @return a page of imports
     */
    @GetMapping("/visa-rules/imports")
    @Operation(summary = "Get the visa rule imports")
    @PreAuthorize("@DestinationPolicy.isStaff(authentication)")
    public ResponseEntity<Paginated<VisaRuleImportResponse>> imports(@Valid @ModelAttribute PaginationFilter filter) {
        return ResponseEntity.ok(importService.getAll(filter));
    }
}
