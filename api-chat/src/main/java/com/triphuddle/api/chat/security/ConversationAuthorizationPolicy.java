package com.triphuddle.api.chat.security;

import com.triphuddle.api.chat.dto.CreatePrivateConversationRequest;
import com.triphuddle.common.security.BaseAuthorizationPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Any signed-in user lists their own conversations and opens private chats; participants see a conversation.
 */
@Component("ConversationPolicy")
@RequiredArgsConstructor
public class ConversationAuthorizationPolicy
        implements BaseAuthorizationPolicy<CreatePrivateConversationRequest, Void, UUID> {

    private final ChatAccess chatAccess;

    @Override
    public boolean canCreate(Authentication authentication, CreatePrivateConversationRequest request) {
        return isAuthenticated(authentication);
    }

    @Override
    public boolean canGetById(Authentication authentication, UUID id) {
        return chatAccess.isParticipant(authentication, id);
    }

    @Override
    public boolean canGetAll(Authentication authentication) {
        // The list only ever holds the caller's own conversations.
        return isAuthenticated(authentication);
    }

    @Override
    public boolean canUpdate(Authentication authentication, Void request) {
        // Group chats follow their group and private chats have nothing to edit.
        return false;
    }

    @Override
    public boolean canDelete(Authentication authentication, UUID id) {
        return false;
    }

    @Override
    public boolean canDisable(Authentication authentication) {
        return false;
    }
}
