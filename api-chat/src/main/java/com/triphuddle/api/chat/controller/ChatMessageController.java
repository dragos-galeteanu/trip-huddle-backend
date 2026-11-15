package com.triphuddle.api.chat.controller;

import com.triphuddle.api.chat.dto.EditMessageRequest;
import com.triphuddle.api.chat.dto.MessageHistoryFilter;
import com.triphuddle.api.chat.dto.MessageResponse;
import com.triphuddle.api.chat.dto.SendMessageRequest;
import com.triphuddle.api.chat.service.ChatMessageService;
import com.triphuddle.common.dto.CursorPaginated;
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
 * REST endpoints for messages: history and sending per conversation, then each message by ID.
 */
@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
@Tag(name = "Chat messages")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    /**
     * Get a page of a conversation's history.
     *
     * @param conversationId the ID of the conversation
     * @param filter the cursor and page size
     * @param authentication the caller
     * @return a page of messages
     */
    @GetMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "Get the messages of a conversation")
    @PreAuthorize("@ChatMessagePolicy.canGetAllByConversation(authentication, #conversationId)")
    public ResponseEntity<CursorPaginated<MessageResponse>> getHistory(
            @PathVariable UUID conversationId, @Valid @ModelAttribute MessageHistoryFilter filter,
            Authentication authentication) {
        return ResponseEntity.ok(
                chatMessageService.getHistory(conversationId, SecurityUtils.extractUserId(authentication), filter));
    }

    /**
     * Send a message to a conversation.
     *
     * @param conversationId the ID of the conversation
     * @param request the content of the message
     * @param authentication the caller
     * @return the sent message
     */
    @PostMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "Send a message")
    @PreAuthorize("@ChatMessagePolicy.canSend(authentication, #conversationId)")
    public ResponseEntity<MessageResponse> send(
            @PathVariable UUID conversationId, @Valid @RequestBody SendMessageRequest request,
            Authentication authentication) {
        MessageResponse response =
                chatMessageService.send(conversationId, SecurityUtils.extractUserId(authentication), request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Get a message.
     *
     * @param id the ID of the message
     * @return the message
     */
    @GetMapping("/messages/{id}")
    @Operation(summary = "Get a message")
    @PreAuthorize("@ChatMessagePolicy.canGetById(authentication, #id)")
    public ResponseEntity<MessageResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(chatMessageService.getById(id));
    }

    /**
     * Edit the text of one of the caller's messages.
     *
     * @param id the ID of the message
     * @param request the new text
     * @param authentication the caller
     * @return the edited message
     */
    @PatchMapping("/messages/{id}")
    @Operation(summary = "Edit a message")
    @PreAuthorize("@ChatMessagePolicy.canEdit(authentication, #id)")
    public ResponseEntity<MessageResponse> edit(
            @PathVariable String id, @Valid @RequestBody EditMessageRequest request, Authentication authentication) {
        return ResponseEntity.ok(chatMessageService.edit(id, SecurityUtils.extractUserId(authentication), request));
    }

    /**
     * Delete a message.
     *
     * @param id the ID of the message
     * @param authentication the caller
     * @return the deleted message, without its content
     */
    @DeleteMapping("/messages/{id}")
    @Operation(summary = "Delete a message")
    @PreAuthorize("@ChatMessagePolicy.canDelete(authentication, #id)")
    public ResponseEntity<MessageResponse> delete(@PathVariable String id, Authentication authentication) {
        return ResponseEntity.ok(chatMessageService.delete(id, SecurityUtils.extractUserId(authentication)));
    }

    /**
     * React to a message with an emoji.
     *
     * @param id the ID of the message
     * @param emoji the emoji, URL-encoded
     * @param authentication the caller
     * @return the message with its reactions
     */
    @PutMapping("/messages/{id}/reactions/{emoji}")
    @Operation(summary = "React to a message")
    @PreAuthorize("@ChatMessagePolicy.canReact(authentication, #id)")
    public ResponseEntity<MessageResponse> addReaction(
            @PathVariable String id, @PathVariable String emoji, Authentication authentication) {
        return ResponseEntity.ok(
                chatMessageService.addReaction(id, SecurityUtils.extractUserId(authentication), emoji));
    }

    /**
     * Remove the caller's reaction to a message.
     *
     * @param id the ID of the message
     * @param emoji the emoji, URL-encoded
     * @param authentication the caller
     * @return the message with its reactions
     */
    @DeleteMapping("/messages/{id}/reactions/{emoji}")
    @Operation(summary = "Remove a reaction from a message")
    @PreAuthorize("@ChatMessagePolicy.canReact(authentication, #id)")
    public ResponseEntity<MessageResponse> removeReaction(
            @PathVariable String id, @PathVariable String emoji, Authentication authentication) {
        return ResponseEntity.ok(
                chatMessageService.removeReaction(id, SecurityUtils.extractUserId(authentication), emoji));
    }
}
