package com.triphuddle.api.chat.mapper;

import com.triphuddle.api.chat.dto.AttachmentResponse;
import com.triphuddle.api.chat.dto.MessageResponse;
import com.triphuddle.api.chat.dto.ReactionResponse;
import com.triphuddle.api.chat.entity.ChatMessage;
import com.triphuddle.api.chat.entity.MessageAttachment;
import com.triphuddle.api.chat.entity.MessageReaction;
import com.triphuddle.api.chat.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maps {@link ChatMessage} to its response DTO, signing download URLs and hiding deleted content.
 */
@Component
@RequiredArgsConstructor
public class ChatMessageMapper {

    private final AttachmentService attachmentService;

    /**
     * Converts a message to what participants see; a deleted message keeps only its place.
     *
     * @param message the message
     * @return the response DTO
     */
    public MessageResponse toResponse(ChatMessage message) {
        return toResponse(message, false);
    }

    /**
     * Converts a message to what staff reviewing a report see, including deleted content.
     *
     * @param message the message
     * @return the response DTO
     */
    public MessageResponse toModerationResponse(ChatMessage message) {
        return toResponse(message, true);
    }

    /**
     * Converts messages to what participants see.
     *
     * @param messages the messages
     * @return the response DTOs
     */
    public List<MessageResponse> toResponseList(List<ChatMessage> messages) {
        return messages.stream().map(this::toResponse).toList();
    }

    private MessageResponse toResponse(ChatMessage message, boolean isContentRevealed) {
        boolean isDeleted = message.getDeletedAt() != null;
        boolean isHidden = isDeleted && !isContentRevealed;

        return new MessageResponse(
                message.getId().toHexString(),
                message.getConversationId(),
                message.getSenderId(),
                message.getClientMessageId(),
                message.getKind(),
                isHidden ? null : message.getBody(),
                message.getSystemEvent(),
                message.getReplyToMessageId() == null ? null : message.getReplyToMessageId().toHexString(),
                isHidden ? List.of() : toAttachmentResponses(message.getAttachments()),
                isHidden ? List.of() : toReactionResponses(message.getReactions()),
                message.getEditedAt() != null,
                message.getEditedAt(),
                isDeleted,
                message.getDeletedAt(),
                message.getCreatedAt()
        );
    }

    private List<AttachmentResponse> toAttachmentResponses(List<MessageAttachment> attachments) {
        if (attachments == null) {
            return List.of();
        }
        return attachments.stream()
                .map(attachment -> new AttachmentResponse(
                        attachment.objectPath(),
                        attachment.fileName(),
                        attachment.contentType(),
                        attachment.sizeBytes(),
                        attachmentService.createDownloadUrl(attachment.objectPath()).orElse(null)))
                .toList();
    }

    private List<ReactionResponse> toReactionResponses(List<MessageReaction> reactions) {
        if (reactions == null || reactions.isEmpty()) {
            return List.of();
        }
        // Grouped in the order each emoji was first used, so the client's reaction bar stays stable.
        Map<String, List<UUID>> usersByEmoji = new LinkedHashMap<>();
        for (MessageReaction reaction : reactions) {
            usersByEmoji.computeIfAbsent(reaction.emoji(), emoji -> new ArrayList<>()).add(reaction.userId());
        }
        return usersByEmoji.entrySet().stream()
                .map(entry -> new ReactionResponse(entry.getKey(), entry.getValue().size(), entry.getValue()))
                .toList();
    }
}
