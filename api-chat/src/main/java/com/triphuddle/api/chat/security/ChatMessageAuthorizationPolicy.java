package com.triphuddle.api.chat.security;

import com.triphuddle.api.chat.dto.EditMessageRequest;
import com.triphuddle.api.chat.dto.SendMessageRequest;
import com.triphuddle.common.security.BaseAuthorizationPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Active participants send and react; senders edit; senders and group Admins delete.
 */
@Component("ChatMessagePolicy")
@RequiredArgsConstructor
public class ChatMessageAuthorizationPolicy
        implements BaseAuthorizationPolicy<SendMessageRequest, EditMessageRequest, String> {

    private final ChatAccess chatAccess;

    @Override
    public Optional<UUID> resolveOwnerUserId(String id) {
        return chatAccess.findSenderId(id);
    }

    @Override
    public boolean canCreate(Authentication authentication, SendMessageRequest request) {
        // The conversation is in the route, not the body: controllers ask canSend instead.
        return false;
    }

    /**
     * Check if the user can send to a conversation, or upload attachments for it.
     *
     * @param authentication the authentication object
     * @param conversationId the ID of the conversation
     * @return true if the user is an active participant
     */
    public boolean canSend(Authentication authentication, UUID conversationId) {
        return chatAccess.isActiveParticipant(authentication, conversationId);
    }

    @Override
    public boolean canGetById(Authentication authentication, String id) {
        return chatAccess.canReadMessage(authentication, id);
    }

    @Override
    public boolean canGetAll(Authentication authentication) {
        return false;
    }

    /**
     * Check if the user can read the history of a conversation.
     *
     * @param authentication the authentication object
     * @param conversationId the ID of the conversation
     * @return true if the user is or was a participant, or is staff and it is a support chat
     */
    public boolean canGetAllByConversation(Authentication authentication, UUID conversationId) {
        return chatAccess.canReadHistory(authentication, conversationId);
    }

    @Override
    public boolean canUpdate(Authentication authentication, EditMessageRequest request) {
        // The message is in the route, not the body: controllers ask canEdit instead.
        return false;
    }

    /**
     * Check if the user can edit a message: only its sender, while still in the conversation.
     *
     * @param authentication the authentication object
     * @param id the ID of the message
     * @return true if the user sent the message and is still an active participant
     */
    public boolean canEdit(Authentication authentication, String id) {
        return isResourceOwner(authentication, id) && chatAccess.isActiveInMessageConversation(authentication, id);
    }

    @Override
    public boolean canDelete(Authentication authentication, String id) {
        return (isResourceOwner(authentication, id) && chatAccess.isActiveInMessageConversation(authentication, id))
                || chatAccess.isGroupAdminOfMessageConversation(authentication, id);
    }

    /**
     * Check if the user can add or remove their reactions to a message.
     *
     * @param authentication the authentication object
     * @param id the ID of the message
     * @return true if the user is an active participant of the message's conversation
     */
    public boolean canReact(Authentication authentication, String id) {
        return chatAccess.isActiveInMessageConversation(authentication, id);
    }

    @Override
    public boolean canDisable(Authentication authentication) {
        return false;
    }
}
