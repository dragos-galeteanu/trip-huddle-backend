package com.triphuddle.api.chat.mapper;

import com.triphuddle.api.chat.dto.ConversationResponse;
import com.triphuddle.api.chat.entity.Conversation;
import com.triphuddle.api.chat.entity.ConversationParticipant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Maps a {@link Conversation} to what one participant sees of it.
 */
@Component
@RequiredArgsConstructor
public class ConversationMapper {

    private final ParticipantMapper participantMapper;

    /**
     * Converts a conversation as seen by one of its participants.
     *
     * @param viewer the participant record of the caller
     * @param participants every participant of the conversation
     * @return the response DTO
     */
    public ConversationResponse toResponse(ConversationParticipant viewer, List<ConversationParticipant> participants) {
        Conversation conversation = viewer.getConversation();

        // An ex-member must not learn what was said after they left, not even from the list preview.
        boolean isLastMessageVisible = conversation.getLastMessageAt() != null
                && (viewer.getLeftAt() == null || !conversation.getLastMessageAt().isAfter(viewer.getLeftAt()));

        return new ConversationResponse(
                conversation.getId(),
                conversation.getType(),
                conversation.getGroupId(),
                conversation.getName(),
                conversation.getReadOnlyAt() != null || viewer.getLeftAt() != null,
                isLastMessageVisible ? conversation.getLastMessageId() : null,
                isLastMessageVisible ? conversation.getLastMessagePreview() : null,
                isLastMessageVisible ? conversation.getLastMessageSenderId() : null,
                isLastMessageVisible ? conversation.getLastMessageAt() : null,
                viewer.getLastActivityAt(),
                viewer.getUnreadCount(),
                viewer.isMutedAt(Instant.now()),
                viewer.isMutedAt(Instant.now()) ? viewer.getMutedUntil() : null,
                viewer.getLastReadMessageId(),
                viewer.getLeftAt(),
                participantMapper.toResponseList(participants),
                conversation.getCreatedAt()
        );
    }
}
