package com.triphuddle.api.chat.api;

import com.triphuddle.api.chat.dto.ChatEvent;
import com.triphuddle.api.chat.dto.ChatEventType;
import com.triphuddle.common.config.StompDestinations;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.ExecutorSubscribableChannel;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stands in for the broker's template: keeps every real-time event sent, so tests can check who got what.
 */
public class RecordingMessagingTemplate extends SimpMessagingTemplate {

    public record Sent(String destination, ChatEvent event) {
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private volatile boolean isFailing;

    public RecordingMessagingTemplate() {
        super(new ExecutorSubscribableChannel());
    }

    @Override
    public void convertAndSend(String destination, Object payload) {
        if (isFailing) {
            throw new MessageDeliveryException("broker unavailable");
        }
        sent.add(new Sent(destination, (ChatEvent) payload));
    }

    public List<Sent> sent() {
        return List.copyOf(sent);
    }

    /** The events of this type that reached this user's own topic. */
    public List<ChatEvent> to(UUID userId, ChatEventType type) {
        String destination = StompDestinations.userTopic(userId);
        return sent.stream()
                .filter(entry -> entry.destination().equals(destination) && entry.event().type() == type)
                .map(Sent::event)
                .toList();
    }

    public List<ChatEvent> toTopic(String destination, ChatEventType type) {
        return sent.stream()
                .filter(entry -> entry.destination().equals(destination) && entry.event().type() == type)
                .map(Sent::event)
                .toList();
    }

    public void failing(boolean isFailing) {
        this.isFailing = isFailing;
    }

    public void reset() {
        sent.clear();
        isFailing = false;
    }
}
