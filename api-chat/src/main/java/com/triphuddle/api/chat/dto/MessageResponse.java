package com.triphuddle.api.chat.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A message; a deleted one keeps its place in the history but has no body, attachments or reactions.
 *
 * @param id the ID of the message
 * @param conversationId the ID of the conversation
 * @param senderId the ID of the user who sent it
 * @param clientMessageId the ID the sender's client generated for it
 * @param body the text of the message, or null if it has none or was deleted
 * @param replyToMessageId the ID of the message it replies to, or null
 * @param attachments the files attached to it
 * @param reactions the reactions to it, grouped by emoji
 * @param isEdited whether its text was edited
 * @param editedAt when its text was last edited, or null
 * @param isDeleted whether it was deleted
 * @param deletedAt when it was deleted, or null
 * @param createdAt when it was sent
 */
public record MessageResponse(
        String id,
        UUID conversationId,
        UUID senderId,
        String clientMessageId,
        String body,
        String replyToMessageId,
        List<AttachmentResponse> attachments,
        List<ReactionResponse> reactions,
        boolean isEdited,
        Instant editedAt,
        boolean isDeleted,
        Instant deletedAt,
        Instant createdAt
) {
}
