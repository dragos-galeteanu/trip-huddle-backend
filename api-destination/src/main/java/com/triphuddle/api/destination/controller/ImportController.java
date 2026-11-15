package com.triphuddle.api.destination.controller;

import com.triphuddle.api.destination.dto.ImportJobResponse;
import com.triphuddle.api.destination.dto.ImportRequest;
import com.triphuddle.api.destination.entity.ImportStatus;
import com.triphuddle.api.destination.service.ImportService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Imports of countries from Wikidata under {@code /api/v1/destinations/imports}, for staff.
 */
@RestController
@RequestMapping("/api/v1/destinations/imports")
@RequiredArgsConstructor
@Tag(name = "Imports")
public class ImportController {

    private final ImportService importService;

    /**
     * Queue an import of a country; it runs in the background.
     *
     * @param request the country and what to include
     * @param authentication the caller
     * @return the queued import, to follow with its ID
     */
    @PostMapping
    @Operation(summary = "Import a country from Wikidata")
    @PreAuthorize("@DestinationPolicy.canManage(authentication)")
    public ResponseEntity<ImportJobResponse> request(@Valid @RequestBody ImportRequest request,
                                                     Authentication authentication) {
        return new ResponseEntity<>(importService.request(request, SecurityUtils.extractUserId(authentication)),
                HttpStatus.ACCEPTED);
    }

    /**
     * Get imports, newest first.
     *
     * @param countryCode the country, or null for all
     * @param status the status, or null for all
     * @param filter the page
     * @return a page of imports
     */
    @GetMapping
    @Operation(summary = "Get imports")
    @PreAuthorize("@DestinationPolicy.isStaff(authentication)")
    public ResponseEntity<Paginated<ImportJobResponse>> getAll(@RequestParam(required = false) String countryCode,
                                                               @RequestParam(required = false) ImportStatus status,
                                                               @Valid @ModelAttribute PaginationFilter filter) {
        return ResponseEntity.ok(importService.getAll(countryCode, status, filter));
    }

    /**
     * Get an import, e.g. to follow its progress.
     *
     * @param id the ID of the import
     * @return the import
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get an import")
    @PreAuthorize("@DestinationPolicy.isStaff(authentication)")
    public ResponseEntity<ImportJobResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(importService.getById(id));
    }
}
