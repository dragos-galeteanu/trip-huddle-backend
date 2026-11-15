package com.triphuddle.api.chat.controller;

import com.triphuddle.api.chat.dto.CreateUploadUrlRequest;
import com.triphuddle.api.chat.dto.UploadUrlResponse;
import com.triphuddle.api.chat.service.AttachmentService;
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
 * REST endpoint handing out upload URLs for attachments; files go straight to the bucket, not through here.
 */
@RestController
@RequestMapping("/api/v1/chats/conversations/{conversationId}/attachments")
@RequiredArgsConstructor
@Tag(name = "Chat attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    /**
     * Get a signed URL to upload one attachment for a conversation.
     *
     * @param conversationId the ID of the conversation
     * @param request the name, type and size of the file
     * @param authentication the caller
     * @return where and how to upload the file
     */
    @PostMapping("/upload-url")
    @Operation(summary = "Get an upload URL for an attachment")
    @PreAuthorize("@ChatMessagePolicy.canSend(authentication, #conversationId)")
    public ResponseEntity<UploadUrlResponse> createUploadUrl(
            @PathVariable UUID conversationId, @Valid @RequestBody CreateUploadUrlRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                attachmentService.createUploadUrl(conversationId, SecurityUtils.extractUserId(authentication), request));
    }
}
