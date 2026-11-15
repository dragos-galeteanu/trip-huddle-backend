package com.triphuddle.api.chat.controller;

import com.triphuddle.api.chat.dto.ChatEventType;
import com.triphuddle.api.chat.dto.TypingPayload;
import com.triphuddle.api.chat.dto.TypingRequest;
import com.triphuddle.api.chat.security.ChatAccess;
import com.triphuddle.api.chat.service.ChatEventPublisher;
import com.triphuddle.api.chat.service.ConversationService;
import com.triphuddle.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * STOMP endpoint for typing indicators; they are relayed to the other participants and never stored.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class TypingController {

    private final ChatAccess chatAccess;
    private final ConversationService conversationService;
    private final ChatEventPublisher chatEventPublisher;

    /**
     * Tell the other active participants that the caller started or stopped typing.
     *
     * @param conversationId the ID of the conversation
     * @param request whether the caller is typing
     * @param principal the authenticated STOMP session
     */
    @MessageMapping("/conversations/{conversationId}/typing")
    public void typing(@DestinationVariable UUID conversationId, @Payload(required = false) TypingRequest request,
                       Principal principal) {
        if (!(principal instanceof Authentication authentication)
                || !chatAccess.isActiveParticipant(authentication, conversationId)) {
            // Ignored rather than refused: a stale typing frame is harmless and not worth an ERROR frame.
            log.debug("Typing indicator ignored [conversationId={}]", conversationId);
            return;
        }

        UUID userId = SecurityUtils.extractUserId(authentication);
        try {
            // Whoever may not send, because the chat is read-only or a block stands, may not type either.
            conversationService.requireWritable(
                    conversationService.requireActiveParticipant(conversationId, userId).getConversation(), userId);
        } catch (RuntimeException e) {
            log.debug("Typing indicator ignored [conversationId={}, reason={}]", conversationId, e.getMessage());
            return;
        }

        boolean isTyping = request == null || !Boolean.FALSE.equals(request.getIsTyping());
        List<UUID> others = conversationService.activeParticipantIds(conversationId).stream()
                .filter(participantId -> !participantId.equals(userId))
                .toList();
        chatEventPublisher.publish(others, ChatEventType.TYPING, conversationId, new TypingPayload(userId, isTyping));
    }
}
