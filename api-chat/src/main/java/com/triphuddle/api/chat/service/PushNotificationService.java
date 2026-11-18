package com.triphuddle.api.chat.service;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.notification.NotificationPushContract;
import com.triphuddle.amqp.notification.SendPushRequest;
import com.triphuddle.api.chat.entity.ChatMessage;
import com.triphuddle.api.chat.entity.Conversation;
import com.triphuddle.api.chat.entity.ConversationParticipant;
import com.triphuddle.api.chat.entity.ConversationType;
import com.triphuddle.api.chat.entity.UserBlock;
import com.triphuddle.api.chat.repository.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Asks the notification service to push a new message to the participants who should hear about it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final UserBlockRepository userBlockRepository;
    private final ChatUserService chatUserService;
    private final RabbitMQMessageProducer messageProducer;

    /**
     * Push a new message to every active participant but the sender, skipping those who muted or block them.
     *
     * @param conversation the conversation
     * @param message the new message
     * @param participants the active participants of the conversation
     * @param preview the text to show in the notification
     */
    public void notifyNewMessage(Conversation conversation, ChatMessage message,
                                 List<ConversationParticipant> participants, String preview) {
        Instant now = Instant.now();
        List<UUID> candidates = participants.stream()
                .filter(participant -> !participant.getUserId().equals(message.getSenderId()))
                .filter(participant -> !participant.isMutedAt(now))
                .map(ConversationParticipant::getUserId)
                .toList();
        if (candidates.isEmpty()) {
            return;
        }

        // In a group chat a blocked sender's messages are still shown, but they should not buzz a phone.
        Set<UUID> blockers = userBlockRepository
                .findByBlockedIdAndBlockerIdInAndDeletedAtIsNull(message.getSenderId(), candidates).stream()
                .map(UserBlock::getBlockerId)
                .collect(Collectors.toSet());
        Set<UUID> inactive = chatUserService.findInactive(candidates);

        List<UUID> recipients = candidates.stream()
                .filter(userId -> !blockers.contains(userId) && !inactive.contains(userId))
                .toList();
        if (recipients.isEmpty()) {
            return;
        }

        // Group chats show the group's name and support chats the ticket's subject.
        String title = conversation.getType() != ConversationType.PRIVATE && conversation.getName() != null
                ? conversation.getName()
                : "New message";
        Map<String, String> data = Map.of(
                "type", "CHAT_MESSAGE",
                "conversationId", conversation.getId().toString(),
                "messageId", message.getId().toHexString(),
                "senderId", message.getSenderId().toString()
        );
        SendPushRequest request = new SendPushRequest(recipients, title, preview, data);

        messageProducer.publishAfterCommit(request, "", NotificationPushContract.PUSH_QUEUE_NAME);
        log.info("Push requested for chat message [messageId={}, recipients={}]", message.getId(), recipients.size());
    }
}
