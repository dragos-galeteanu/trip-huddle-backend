package com.triphuddle.api.chat.service;

import com.triphuddle.api.chat.dto.ChatEvent;
import com.triphuddle.api.chat.dto.ChatEventType;
import com.triphuddle.common.config.StompDestinations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Sends real-time events to the topic of each user concerned, after the current transaction commits.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Send an event to each of the users.
     *
     * @param userIds the IDs of the users to notify
     * @param type the kind of event
     * @param conversationId the ID of the conversation it concerns
     * @param payload the payload of the event, or null
     */
    public void publish(Collection<UUID> userIds, ChatEventType type, UUID conversationId, Object payload) {
        if (userIds.isEmpty()) {
            return;
        }
        ChatEvent event = new ChatEvent(type, conversationId, payload, Instant.now());
        List<UUID> recipients = List.copyOf(userIds);
        AfterCommit.run(() -> recipients.forEach(userId -> send(userId, event)));
    }

    /**
     * Send an event to a shared topic, such as the support queue topic staff subscribe to.
     *
     * @param destination the destination of the topic
     * @param type the kind of event
     * @param conversationId the ID of the conversation it concerns
     * @param payload the payload of the event, or null
     */
    public void publishToTopic(String destination, ChatEventType type, UUID conversationId, Object payload) {
        ChatEvent event = new ChatEvent(type, conversationId, payload, Instant.now());
        AfterCommit.run(() -> send(destination, event));
    }

    private void send(UUID userId, ChatEvent event) {
        send(StompDestinations.userTopic(userId), event);
    }

    private void send(String destination, ChatEvent event) {
        try {
            messagingTemplate.convertAndSend(destination, event);
        } catch (MessagingException e) {
            // A broker hiccup must not fail the request: the change is saved and clients catch up on refetch.
            log.warn("Could not deliver chat event [type={}, conversationId={}, destination={}]",
                    event.type(), event.conversationId(), destination, e);
        }
    }
}
