package com.triphuddle.api.chat.service;

import com.triphuddle.api.chat.dto.ChatEventType;
import com.triphuddle.api.chat.entity.Conversation;
import com.triphuddle.api.chat.entity.ConversationParticipant;
import com.triphuddle.api.chat.entity.ConversationType;
import com.triphuddle.api.chat.entity.ParticipantRole;
import com.triphuddle.api.chat.repository.ConversationParticipantRepository;
import com.triphuddle.api.chat.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps every group's chat in step with api-group's events. Every handler is safe to run twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GroupConversationSyncService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ChatEventPublisher chatEventPublisher;

    /**
     * Create the chat of a new group, or name it if a membership event created it first.
     *
     * @param groupId the ID of the group
     * @param name the name of the group
     */
    public void onGroupCreated(UUID groupId, String name) {
        Conversation conversation = findOrCreate(groupId);
        conversation.setName(name);
        conversationRepository.save(conversation);
    }

    /**
     * Follow a rename of the group.
     *
     * @param groupId the ID of the group
     * @param name the new name of the group
     */
    public void onGroupUpdated(UUID groupId, String name) {
        Conversation conversation = findOrCreate(groupId);
        conversation.setName(name);
        conversationRepository.save(conversation);
        notifyActive(conversation);
    }

    /**
     * Make the chat of a disabled or deleted group read-only; its history stays readable.
     *
     * @param groupId the ID of the group
     */
    public void onGroupClosed(UUID groupId) {
        Conversation conversation = findOrCreate(groupId);
        if (conversation.getReadOnlyAt() == null) {
            conversation.setReadOnlyAt(Instant.now());
            conversationRepository.save(conversation);
            log.info("Group conversation made read-only [conversationId={}, groupId={}]", conversation.getId(), groupId);
        }
        notifyActive(conversation);
    }

    /**
     * Add a new member to the group's chat, or bring back one who left.
     *
     * @param groupId the ID of the group
     * @param userId the ID of the member
     * @param role the role of the member in the group
     */
    public void onMemberJoined(UUID groupId, UUID userId, String role) {
        Conversation conversation = findOrCreate(groupId);
        Instant now = ConversationService.now();

        ConversationParticipant participant = participantRepository
                .findByConversationIdAndUserId(conversation.getId(), userId)
                .orElseGet(() -> {
                    ConversationParticipant created = new ConversationParticipant();
                    created.setConversation(conversation);
                    created.setUserId(userId);
                    created.setJoinedAt(now);
                    return created;
                });
        // A returning member sees the whole history again, as every member does.
        participant.setLeftAt(null);
        participant.setRole(toParticipantRole(role));
        participant.setLastActivityAt(now);
        participantRepository.save(participant);
        log.info("Group conversation participant joined [conversationId={}, userId={}, role={}]",
                conversation.getId(), userId, participant.getRole());

        notifyActive(conversation);
    }

    /**
     * Follow a member's change of role in the group.
     *
     * @param groupId the ID of the group
     * @param userId the ID of the member
     * @param role the new role of the member
     */
    public void onMemberRoleChanged(UUID groupId, UUID userId, String role) {
        findParticipant(groupId, userId).ifPresent(participant -> {
            participant.setRole(toParticipantRole(role));
            participantRepository.save(participant);
        });
    }

    /**
     * Mark a member who left, or was removed from, the group as having left its chat.
     *
     * @param groupId the ID of the group
     * @param userId the ID of the member
     */
    public void onMemberLeft(UUID groupId, UUID userId) {
        findParticipant(groupId, userId)
                .filter(ConversationParticipant::isActive)
                .ifPresent(participant -> {
                    participant.setLeftAt(Instant.now());
                    participantRepository.save(participant);
                    log.info("Group conversation participant left [conversationId={}, userId={}]",
                            participant.getConversation().getId(), userId);

                    UUID conversationId = participant.getConversation().getId();
                    List<UUID> recipients = new ArrayList<>(activeIds(conversationId));
                    recipients.add(userId);
                    chatEventPublisher.publish(recipients, ChatEventType.CONVERSATION_UPDATED, conversationId, null);
                });
    }

    private Optional<ConversationParticipant> findParticipant(UUID groupId, UUID userId) {
        return conversationRepository.findByGroupId(groupId)
                .flatMap(conversation -> participantRepository.findByConversationIdAndUserId(conversation.getId(), userId));
    }

    private Conversation findOrCreate(UUID groupId) {
        // The group's events arrive on separate queues, so a membership can come before its group.
        // Two creating at once collide on the unique group ID; the listener's retry then finds it.
        return conversationRepository.findByGroupId(groupId).orElseGet(() -> {
            Conversation conversation = new Conversation();
            conversation.setType(ConversationType.GROUP);
            conversation.setGroupId(groupId);
            Conversation saved = conversationRepository.saveAndFlush(conversation);
            log.info("Group conversation created [id={}, groupId={}]", saved.getId(), groupId);
            return saved;
        });
    }

    private void notifyActive(Conversation conversation) {
        chatEventPublisher.publish(activeIds(conversation.getId()), ChatEventType.CONVERSATION_UPDATED,
                conversation.getId(), null);
    }

    private List<UUID> activeIds(UUID conversationId) {
        return participantRepository.findByConversationIdAndLeftAtIsNull(conversationId).stream()
                .map(ConversationParticipant::getUserId)
                .toList();
    }

    private static ParticipantRole toParticipantRole(String role) {
        return "ADMIN".equals(role) ? ParticipantRole.ADMIN : ParticipantRole.MEMBER;
    }
}
