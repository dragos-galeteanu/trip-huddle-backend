package com.triphuddle.api.user.controller;

import com.triphuddle.api.user.dto.TravelDocumentsRequest;
import com.triphuddle.api.user.dto.TravelDocumentsResponse;
import com.triphuddle.api.user.service.UserTravelDocumentService;
import com.triphuddle.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's nationalities and residence permits under {@code /api/v1/users/me/travel-documents}.
 */
@RestController
@RequestMapping("/api/v1/users/me/travel-documents")
@RequiredArgsConstructor
@Tag(name = "User travel documents")
public class UserTravelDocumentController {

    private final UserTravelDocumentService service;

    /**
     * Get the caller's nationalities and residence permits.
     *
     * @param authentication the caller
     * @return the documents
     */
    @GetMapping
    @Operation(summary = "Get my travel documents")
    @PreAuthorize("@UserTravelDocumentPolicy.canGetMine(authentication)")
    public ResponseEntity<TravelDocumentsResponse> get(Authentication authentication) {
        return ResponseEntity.ok(service.get(SecurityUtils.extractUserId(authentication)));
    }

    /**
     * Replace the caller's nationalities and residence permits; visa guidance follows them shortly after.
     *
     * @param request every document they hold
     * @param authentication the caller
     * @return the documents
     */
    @PutMapping
    @Operation(summary = "Replace my travel documents")
    @PreAuthorize("@UserTravelDocumentPolicy.canUpdate(authentication, #request)")
    public ResponseEntity<TravelDocumentsResponse> replace(@Valid @RequestBody TravelDocumentsRequest request,
                                                           Authentication authentication) {
        return ResponseEntity.ok(service.replace(SecurityUtils.extractUserId(authentication), request));
    }
}
