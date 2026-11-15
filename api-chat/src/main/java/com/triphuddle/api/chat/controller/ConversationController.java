package com.triphuddle.api.chat.controller;

import com.triphuddle.api.chat.dto.ConversationResponse;
import com.triphuddle.api.chat.dto.CreatePrivateConversationRequest;
import com.triphuddle.api.chat.dto.MarkConversationReadRequest;
import com.triphuddle.api.chat.dto.MuteConversationRequest;
import com.triphuddle.api.chat.service.ConversationService;
import com.triphuddle.common.dto.CursorPaginated;
import com.triphuddle.common.filter.CursorPaginationFilter;
import com.triphuddle.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST endpoints for the caller's conversations under {@code /api/v1/chats/conversations}.
 */
@RestController
@RequestMapping("/api/v1/chats/conversations")
@RequiredArgsConstructor
@Tag(name = "Conversations")
public class ConversationController {

    private final ConversationService conversationService;

    /**
     * Get the caller's conversations, most recently active first.
     *
     * @param filter the cursor and page size
     * @param authentication the caller
     * @return a page of conversations
     */
    @GetMapping
    @Operation(summary = "Get my conversations")
    @PreAuthorize("@ConversationPolicy.canGetAll(authentication)")
    public ResponseEntity<CursorPaginated<ConversationResponse>> getMine(
            @Valid @ModelAttribute CursorPaginationFilter filter, Authentication authentication) {
        return ResponseEntity.ok(conversationService.getMine(SecurityUtils.extractUserId(authentication), filter));
    }

    /**
     * Get the private chat with another user, creating it on first use.
     *
     * @param request the other user
     * @param authentication the caller
     * @return the conversation
     */
    @PostMapping("/private")
    @Operation(summary = "Open the private chat with a user")
    @PreAuthorize("@ConversationPolicy.canCreate(authentication, #request)")
    public ResponseEntity<ConversationResponse> openPrivate(
            @Valid @RequestBody CreatePrivateConversationRequest request, Authentication authentication) {
        return ResponseEntity.ok(conversationService.openPrivate(SecurityUtils.extractUserId(authentication), request));
    }

    /**
     * Get a conversation.
     *
     * @param id the ID of the conversation
     * @param authentication the caller
     * @return the conversation
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a conversation")
    @PreAuthorize("@ConversationPolicy.canGetById(authentication, #id)")
    public ResponseEntity<ConversationResponse> getById(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(conversationService.getForUser(id, SecurityUtils.extractUserId(authentication)));
    }

    /**
     * Mute or unmute a conversation's push notifications for the caller.
     *
     * @param id the ID of the conversation
     * @param request whether to mute, and until when
     * @param authentication the caller
     * @return the conversation
     */
    @PutMapping("/{id}/mute")
    @Operation(summary = "Mute or unmute a conversation")
    @PreAuthorize("@ConversationPolicy.canGetById(authentication, #id)")
    public ResponseEntity<ConversationResponse> mute(
            @PathVariable UUID id, @Valid @RequestBody MuteConversationRequest request, Authentication authentication) {
        return ResponseEntity.ok(conversationService.mute(id, SecurityUtils.extractUserId(authentication), request));
    }

    /**
     * Mark a conversation read up to a message.
     *
     * @param id the ID of the conversation
     * @param request the last message read
     * @param authentication the caller
     * @return the conversation
     */
    @PostMapping("/{id}/read")
    @Operation(summary = "Mark a conversation read up to a message")
    @PreAuthorize("@ConversationPolicy.canGetById(authentication, #id)")
    public ResponseEntity<ConversationResponse> markRead(
            @PathVariable UUID id, @Valid @RequestBody MarkConversationReadRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(conversationService.markRead(id, SecurityUtils.extractUserId(authentication), request));
    }
}
