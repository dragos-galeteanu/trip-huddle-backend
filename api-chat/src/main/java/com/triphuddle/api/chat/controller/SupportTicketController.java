package com.triphuddle.api.chat.controller;

import com.triphuddle.api.chat.dto.ChangeTicketStatusRequest;
import com.triphuddle.api.chat.dto.CreateSupportTicketRequest;
import com.triphuddle.api.chat.dto.RateSupportTicketRequest;
import com.triphuddle.api.chat.dto.SupportTicketEventResponse;
import com.triphuddle.api.chat.dto.SupportTicketQueueFilter;
import com.triphuddle.api.chat.dto.SupportTicketResponse;
import com.triphuddle.api.chat.dto.UpdateSupportTicketRequest;
import com.triphuddle.api.chat.entity.TicketStatus;
import com.triphuddle.api.chat.security.ChatAccess;
import com.triphuddle.api.chat.service.SupportTicketService;
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
 * REST endpoints for support tickets under {@code /api/v1/chats/support-tickets}; their messages use the chat routes.
 */
@RestController
@RequestMapping("/api/v1/chats/support-tickets")
@RequiredArgsConstructor
@Tag(name = "Support tickets")
public class SupportTicketController {

    private final SupportTicketService supportTicketService;
    private final ChatAccess chatAccess;

    /**
     * Open a ticket with its first message, for the caller or, as staff, towards another user.
     *
     * @param request the ticket and its first message
     * @param authentication the caller
     * @return the opened ticket
     */
    @PostMapping
    @Operation(summary = "Open a support ticket")
    @PreAuthorize("@SupportTicketPolicy.canCreate(authentication, #request)")
    public ResponseEntity<SupportTicketResponse> open(
            @Valid @RequestBody CreateSupportTicketRequest request, Authentication authentication) {
        SupportTicketResponse response = supportTicketService.open(SecurityUtils.extractUserId(authentication), request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Get the caller's own tickets, newest first.
     *
     * @param status the status to keep, or all if not given
     * @param filter the cursor and page size
     * @param authentication the caller
     * @return a page of tickets
     */
    @GetMapping("/mine")
    @Operation(summary = "Get my support tickets")
    @PreAuthorize("@SupportTicketPolicy.canGetMine(authentication)")
    public ResponseEntity<CursorPaginated<SupportTicketResponse>> getMine(
            @RequestParam(required = false) TicketStatus status, @Valid @ModelAttribute CursorPaginationFilter filter,
            Authentication authentication) {
        return ResponseEntity.ok(
                supportTicketService.getMine(SecurityUtils.extractUserId(authentication), status, filter));
    }

    /**
     * Get the staff queue, most urgent first and then oldest first.
     *
     * @param filter the cursor, page size and filters
     * @return a page of tickets
     */
    @GetMapping
    @Operation(summary = "Get the support ticket queue")
    @PreAuthorize("@SupportTicketPolicy.canGetAll(authentication)")
    public ResponseEntity<CursorPaginated<SupportTicketResponse>> getQueue(
            @Valid @ModelAttribute SupportTicketQueueFilter filter) {
        return ResponseEntity.ok(supportTicketService.getQueue(filter));
    }

    /**
     * Get a ticket.
     *
     * @param id the ID of the ticket
     * @param authentication the caller
     * @return the ticket
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a support ticket")
    @PreAuthorize("@SupportTicketPolicy.canGetById(authentication, #id)")
    public ResponseEntity<SupportTicketResponse> getById(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(supportTicketService.getById(id, chatAccess.isSupportStaff(authentication)));
    }

    /**
     * Change a ticket's priority or category.
     *
     * @param id the ID of the ticket
     * @param request the new priority and category
     * @param authentication the caller
     * @return the ticket
     */
    @PatchMapping("/{id}")
    @Operation(summary = "Change a support ticket's priority or category")
    @PreAuthorize("@SupportTicketPolicy.canUpdate(authentication, #request)")
    public ResponseEntity<SupportTicketResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateSupportTicketRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                supportTicketService.update(id, SecurityUtils.extractUserId(authentication), request));
    }

    /**
     * Move a ticket to another status, or reopen it.
     *
     * @param id the ID of the ticket
     * @param request the status to move to
     * @param authentication the caller
     * @return the ticket
     */
    @PatchMapping("/{id}/status")
    @Operation(summary = "Change a support ticket's status")
    @PreAuthorize("@SupportTicketPolicy.canChangeStatus(authentication, #id)")
    public ResponseEntity<SupportTicketResponse> changeStatus(
            @PathVariable UUID id, @Valid @RequestBody ChangeTicketStatusRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(supportTicketService.changeStatus(id, SecurityUtils.extractUserId(authentication),
                chatAccess.isSupportStaff(authentication), request));
    }

    /**
     * Rate the support of a resolved or closed ticket.
     *
     * @param id the ID of the ticket
     * @param request the rating and an optional comment
     * @param authentication the caller
     * @return the ticket
     */
    @PutMapping("/{id}/rating")
    @Operation(summary = "Rate a support ticket")
    @PreAuthorize("@SupportTicketPolicy.canRate(authentication, #id)")
    public ResponseEntity<SupportTicketResponse> rate(
            @PathVariable UUID id, @Valid @RequestBody RateSupportTicketRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(supportTicketService.rate(id, SecurityUtils.extractUserId(authentication), request));
    }

    /**
     * Claim an unassigned ticket.
     *
     * @param id the ID of the ticket
     * @param authentication the caller
     * @return the ticket
     */
    @PostMapping("/{id}/claim")
    @Operation(summary = "Claim a support ticket")
    @PreAuthorize("@SupportTicketPolicy.canHandle(authentication)")
    public ResponseEntity<SupportTicketResponse> claim(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(supportTicketService.claim(id, SecurityUtils.extractUserId(authentication)));
    }

    /**
     * Join a ticket's chat to help its assignee.
     *
     * @param id the ID of the ticket
     * @param authentication the caller
     * @return the ticket
     */
    @PostMapping("/{id}/join")
    @Operation(summary = "Join a support ticket")
    @PreAuthorize("@SupportTicketPolicy.canHandle(authentication)")
    public ResponseEntity<SupportTicketResponse> join(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(supportTicketService.join(id, SecurityUtils.extractUserId(authentication)));
    }

    /**
     * Leave a ticket's chat.
     *
     * @param id the ID of the ticket
     * @param authentication the caller
     * @return the ticket
     */
    @PostMapping("/{id}/leave")
    @Operation(summary = "Leave a support ticket")
    @PreAuthorize("@SupportTicketPolicy.canHandle(authentication)")
    public ResponseEntity<SupportTicketResponse> leave(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(supportTicketService.leave(id, SecurityUtils.extractUserId(authentication)));
    }

    /**
     * Give up a ticket's assignment, putting it back in the queue.
     *
     * @param id the ID of the ticket
     * @param authentication the caller
     * @return the ticket
     */
    @PostMapping("/{id}/release")
    @Operation(summary = "Release a support ticket")
    @PreAuthorize("@SupportTicketPolicy.canRelease(authentication, #id)")
    public ResponseEntity<SupportTicketResponse> release(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(supportTicketService.release(id, SecurityUtils.extractUserId(authentication)));
    }

    /**
     * Get a ticket's history.
     *
     * @param id the ID of the ticket
     * @return the events of the ticket, oldest first
     */
    @GetMapping("/{id}/events")
    @Operation(summary = "Get a support ticket's history")
    @PreAuthorize("@SupportTicketPolicy.canHandle(authentication)")
    public ResponseEntity<List<SupportTicketEventResponse>> getEvents(@PathVariable UUID id) {
        return ResponseEntity.ok(supportTicketService.getEvents(id));
    }
}
