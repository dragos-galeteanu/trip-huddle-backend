package com.triphuddle.api.chat.controller;

import com.triphuddle.api.chat.dto.CreateMessageReportRequest;
import com.triphuddle.api.chat.dto.MessageReportResponse;
import com.triphuddle.api.chat.dto.MessageResponse;
import com.triphuddle.api.chat.dto.ResolveMessageReportRequest;
import com.triphuddle.api.chat.entity.ReportStatus;
import com.triphuddle.api.chat.service.MessageReportService;
import com.triphuddle.common.dto.CursorPaginated;
import com.triphuddle.common.filter.CursorPaginationFilter;
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

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for reporting messages, and for staff to review the reports.
 */
@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
@Tag(name = "Message reports")
public class MessageReportController {

    private final MessageReportService messageReportService;

    /**
     * Report a message.
     *
     * @param messageId the ID of the message
     * @param request why it is reported
     * @param authentication the caller
     * @return the report
     */
    @PostMapping("/messages/{messageId}/reports")
    @Operation(summary = "Report a message")
    @PreAuthorize("@MessageReportPolicy.canReport(authentication, #messageId)")
    public ResponseEntity<MessageReportResponse> create(
            @PathVariable String messageId, @Valid @RequestBody CreateMessageReportRequest request,
            Authentication authentication) {
        MessageReportResponse response =
                messageReportService.create(messageId, SecurityUtils.extractUserId(authentication), request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Get the reports with a status, oldest first.
     *
     * @param status the status of the reports, OPEN unless given
     * @param filter the cursor and page size
     * @return a page of reports
     */
    @GetMapping("/reports")
    @Operation(summary = "Get message reports")
    @PreAuthorize("@MessageReportPolicy.canGetAll(authentication)")
    public ResponseEntity<CursorPaginated<MessageReportResponse>> getAll(
            @RequestParam(defaultValue = "OPEN") ReportStatus status,
            @Valid @ModelAttribute CursorPaginationFilter filter) {
        return ResponseEntity.ok(messageReportService.getAll(status, filter));
    }

    /**
     * Get a report with the reported message.
     *
     * @param id the ID of the report
     * @return the report
     */
    @GetMapping("/reports/{id}")
    @Operation(summary = "Get a message report")
    @PreAuthorize("@MessageReportPolicy.canGetById(authentication, #id)")
    public ResponseEntity<MessageReportResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(messageReportService.getById(id));
    }

    /**
     * Get the messages around the reported one.
     *
     * @param id the ID of the report
     * @return the messages, oldest first
     */
    @GetMapping("/reports/{id}/context")
    @Operation(summary = "Get the messages around a reported message")
    @PreAuthorize("@MessageReportPolicy.canGetById(authentication, #id)")
    public ResponseEntity<List<MessageResponse>> getContext(@PathVariable UUID id) {
        return ResponseEntity.ok(messageReportService.getContext(id));
    }

    /**
     * Resolve a report.
     *
     * @param id the ID of the report
     * @param request the outcome
     * @param authentication the caller
     * @return the resolved report
     */
    @PatchMapping("/reports/{id}")
    @Operation(summary = "Resolve a message report")
    @PreAuthorize("@MessageReportPolicy.canUpdate(authentication, #request)")
    public ResponseEntity<MessageReportResponse> resolve(
            @PathVariable UUID id, @Valid @RequestBody ResolveMessageReportRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                messageReportService.resolve(id, SecurityUtils.extractUserId(authentication), request));
    }
}
