package com.triphuddle.api.destination.controller;

import com.triphuddle.api.destination.content.ContentReportService;
import com.triphuddle.api.destination.content.ContentService;
import com.triphuddle.api.destination.dto.ContentReportRequest;
import com.triphuddle.api.destination.dto.ContentReportResponse;
import com.triphuddle.api.destination.dto.ContentSectionResponse;
import com.triphuddle.api.destination.dto.RegenerateContentRequest;
import com.triphuddle.api.destination.entity.ContentSection;
import com.triphuddle.api.destination.security.DestinationAuthorizationPolicy;
import com.triphuddle.common.dto.CursorPaginated;
import com.triphuddle.common.filter.CursorPaginationFilter;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * AI content of places under {@code /api/v1/destinations}: read, report, and regenerate as staff.
 */
@RestController
@RequestMapping("/api/v1/destinations")
@RequiredArgsConstructor
@Tag(name = "Place content")
public class ContentController {

    private final ContentService contentService;
    private final ContentReportService reportService;
    private final DestinationAuthorizationPolicy destinationPolicy;

    /**
     * Get a place's AI content in a language; sections still being written come back GENERATING, so ask again.
     *
     * @param id the ID of the place
     * @param language the language, ISO 639-1; without it, the Accept-Language header, else English
     * @param acceptLanguage the Accept-Language header
     * @param sections the sections, or none for all
     * @param groupId the group the caller acts for, or null
     * @param authentication the caller
     * @return the sections
     */
    @GetMapping("/places/{id}/content")
    @Operation(summary = "Get a place's AI content")
    @PreAuthorize("@DestinationPolicy.canBrowse(authentication, #groupId)")
    public ResponseEntity<List<ContentSectionResponse>> read(
            @PathVariable UUID id,
            @RequestParam(required = false) String language,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
            @RequestParam(required = false) List<ContentSection> sections,
            @RequestParam(required = false) UUID groupId,
            Authentication authentication) {
        return ResponseEntity.ok(contentService.read(id, language != null ? language : acceptLanguage, sections,
                SecurityUtils.extractUserId(authentication), destinationPolicy.isStaff(authentication)));
    }

    /**
     * Report a section that is wrong or unsuitable.
     *
     * @param id the ID of the place
     * @param section the section
     * @param request the language read and why
     * @param groupId the group the caller acts for, or null
     * @param authentication the caller
     * @return the report
     */
    @PostMapping("/places/{id}/content/{section}/report")
    @Operation(summary = "Report a place's AI content")
    @PreAuthorize("@DestinationPolicy.canBrowse(authentication, #groupId)")
    public ResponseEntity<ContentReportResponse> report(@PathVariable UUID id, @PathVariable ContentSection section,
                                                        @Valid @RequestBody ContentReportRequest request,
                                                        @RequestParam(required = false) UUID groupId,
                                                        Authentication authentication) {
        return new ResponseEntity<>(reportService.report(id, section, request.getLanguage(), request.getReason(),
                SecurityUtils.extractUserId(authentication)), HttpStatus.CREATED);
    }

    /**
     * Regenerate sections now, however fresh.
     *
     * @param id the ID of the place
     * @param request the language and sections
     * @return the sections as they stand
     */
    @PostMapping("/places/{id}/content/regenerate")
    @Operation(summary = "Regenerate a place's AI content")
    @PreAuthorize("@DestinationPolicy.canManage(authentication)")
    public ResponseEntity<List<ContentSectionResponse>> regenerate(@PathVariable UUID id,
                                                                   @RequestBody RegenerateContentRequest request) {
        return new ResponseEntity<>(contentService.regenerate(id, request.getLanguage(), request.getSections()),
                HttpStatus.ACCEPTED);
    }

    /**
     * Get content reports, oldest first.
     *
     * @param isOpen true for open reports, false for resolved ones
     * @param filter the cursor and page size
     * @return a page of reports
     */
    @GetMapping("/content-reports")
    @Operation(summary = "Get content reports")
    @PreAuthorize("@DestinationPolicy.isStaff(authentication)")
    public ResponseEntity<CursorPaginated<ContentReportResponse>> reports(
            @RequestParam(defaultValue = "true") boolean isOpen, @Valid @ModelAttribute CursorPaginationFilter filter) {
        return ResponseEntity.ok(reportService.getAll(isOpen, filter));
    }

    /**
     * Resolve a report, keeping or regenerating its section.
     *
     * @param reportId the ID of the report
     * @param isRegenerating true to regenerate the section
     * @param authentication the caller
     * @return the report
     */
    @PostMapping("/content-reports/{reportId}/resolve")
    @Operation(summary = "Resolve a content report")
    @PreAuthorize("@DestinationPolicy.canManage(authentication)")
    public ResponseEntity<ContentReportResponse> resolve(@PathVariable UUID reportId,
                                                         @RequestParam boolean isRegenerating,
                                                         Authentication authentication) {
        return ResponseEntity.ok(reportService.resolve(reportId, isRegenerating,
                SecurityUtils.extractUserId(authentication)));
    }
}
