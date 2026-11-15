package com.triphuddle.api.chat.security;

import com.triphuddle.api.chat.entity.ChatMessage;
import com.triphuddle.api.chat.entity.ConversationParticipant;
import com.triphuddle.api.chat.entity.ConversationType;
import com.triphuddle.api.chat.entity.ParticipantRole;
import com.triphuddle.api.chat.repository.ChatMessageRepository;
import com.triphuddle.api.chat.repository.ConversationParticipantRepository;
import com.triphuddle.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Answers what the caller is within a conversation, from their participant record; shared by the chat policies.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatAccess {

    private final ConversationParticipantRepository participantRepository;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * Check if the caller is or was a participant, which is enough to read the history they were part of.
     *
     * @param authentication the authentication object
     * @param conversationId the ID of the conversation
     * @return true if the caller has a participant record in the conversation
     */
    public boolean isParticipant(Authentication authentication, UUID conversationId) {
        return findParticipant(authentication, conversationId).isPresent();
    }

    /**
     * Check if the caller is an active participant, who can send, react and type.
     *
     * @param authentication the authentication object
     * @param conversationId the ID of the conversation
     * @return true if the caller is a participant who has not left
     */
    public boolean isActiveParticipant(Authentication authentication, UUID conversationId) {
        return findParticipant(authentication, conversationId)
                .map(ConversationParticipant::isActive)
                .orElse(false);
    }

    /**
     * Check if the caller can read a message: it is in their conversation and not after they left.
     *
     * @param authentication the authentication object
     * @param messageId the ID of the message
     * @return true if the caller can see the message
     */
    public boolean canReadMessage(Authentication authentication, String messageId) {
        return findMessage(messageId)
                .flatMap(message -> findParticipant(authentication, message.getConversationId())
                        .filter(participant -> isVisibleTo(message, participant)))
                .isPresent();
    }

    /**
     * Check if the caller can still act on a message: they are active in its conversation.
     *
     * @param authentication the authentication object
     * @param messageId the ID of the message
     * @return true if the caller is an active participant of the message's conversation
     */
    public boolean isActiveInMessageConversation(Authentication authentication, String messageId) {
        return findMessage(messageId)
                .map(message -> isActiveParticipant(authentication, message.getConversationId()))
                .orElse(false);
    }

    /**
     * Check if the caller is an active Admin of the group chat a message is in.
     *
     * @param authentication the authentication object
     * @param messageId the ID of the message
     * @return true if the caller can moderate the message as a group Admin
     */
    public boolean isGroupAdminOfMessageConversation(Authentication authentication, String messageId) {
        return findMessage(messageId)
                .flatMap(message -> findParticipant(authentication, message.getConversationId()))
                .filter(ConversationParticipant::isActive)
                .filter(participant -> participant.getRole() == ParticipantRole.ADMIN)
                .filter(participant -> participant.getConversation().getType() == ConversationType.GROUP)
                .isPresent();
    }

    /**
     * Find the sender of a message.
     *
     * @param messageId the ID of the message
     * @return the ID of the sender, or empty if the message does not exist
     */
    public Optional<UUID> findSenderId(String messageId) {
        return findMessage(messageId).map(ChatMessage::getSenderId);
    }

    /**
     * Whether a message falls within what a participant may see.
     *
     * @param message the message
     * @param participant the participant
     * @return true if the participant is active or the message predates their leaving
     */
    public static boolean isVisibleTo(ChatMessage message, ConversationParticipant participant) {
        return participant.getLeftAt() == null
                || (message.getCreatedAt() != null && !message.getCreatedAt().isAfter(participant.getLeftAt()));
    }

    private Optional<ConversationParticipant> findParticipant(Authentication authentication, UUID conversationId) {
        if (conversationId == null) {
            return Optional.empty();
        }
        return currentUserId(authentication)
                .flatMap(userId -> participantRepository.findByConversationIdAndUserId(conversationId, userId));
    }

    private Optional<ChatMessage> findMessage(String messageId) {
        if (messageId == null || !ObjectId.isValid(messageId)) {
            return Optional.empty();
        }
        return chatMessageRepository.findById(new ObjectId(messageId));
    }

    private Optional<UUID> currentUserId(Authentication authentication) {
        try {
            return Optional.of(SecurityUtils.extractUserId(authentication));
        } catch (AccessDeniedException e) {
            // A token without a platform userId belongs to no conversation.
            return Optional.empty();
        }
    }
}
