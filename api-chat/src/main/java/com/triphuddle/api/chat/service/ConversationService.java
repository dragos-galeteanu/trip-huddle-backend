package com.triphuddle.api.chat.service;

import com.triphuddle.api.chat.dto.ChatEventType;
import com.triphuddle.api.chat.dto.ConversationListFilter;
import com.triphuddle.api.chat.dto.ConversationResponse;
import com.triphuddle.api.chat.dto.CreatePrivateConversationRequest;
import com.triphuddle.api.chat.dto.MarkConversationReadRequest;
import com.triphuddle.api.chat.dto.MuteConversationRequest;
import com.triphuddle.api.chat.dto.ReadReceiptPayload;
import com.triphuddle.api.chat.entity.ChatMessage;
import com.triphuddle.api.chat.entity.Conversation;
import com.triphuddle.api.chat.entity.ConversationParticipant;
import com.triphuddle.api.chat.entity.ConversationType;
import com.triphuddle.api.chat.entity.MessageAttachment;
import com.triphuddle.api.chat.entity.MessageKind;
import com.triphuddle.api.chat.entity.ParticipantRole;
import com.triphuddle.api.chat.entity.SupportTicket;
import com.triphuddle.api.chat.mapper.ConversationMapper;
import com.triphuddle.api.chat.mapper.SupportTicketMapper;
import com.triphuddle.api.chat.repository.ChatMessageRepository;
import com.triphuddle.api.chat.repository.ConversationParticipantRepository;
import com.triphuddle.api.chat.repository.ConversationRepository;
import com.triphuddle.api.chat.repository.SupportTicketRepository;
import com.triphuddle.api.chat.repository.UserBlockRepository;
import com.triphuddle.api.chat.security.ChatAccess;
import com.triphuddle.common.dto.CursorPaginated;
import com.triphuddle.common.exception.InvalidFilterException;
import com.triphuddle.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manages conversations as their participants see them: the list, private chats, mutes and read receipts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ConversationService {

    private static final int PREVIEW_LENGTH = 100;

    private final ConversationRepository conversationRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final SupportTicketMapper supportTicketMapper;
    private final ConversationParticipantRepository participantRepository;
    private final UserBlockRepository userBlockRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MongoTemplate mongoTemplate;
    private final ChatUserService chatUserService;
    private final ConversationMapper conversationMapper;
    private final ChatEventPublisher chatEventPublisher;

    /**
     * Get the caller's conversations, most recently active first, including those they left.
     *
     * @param userId the ID of the caller
     * @param filter the cursor, page size and optional type
     * @return a page of conversations
     */
    @Transactional(readOnly = true)
    public CursorPaginated<ConversationResponse> getMine(UUID userId, ConversationListFilter filter) {
        int limit = filter.getLimit();
        PageRequest page = PageRequest.of(0, limit + 1);

        List<ConversationParticipant> found;
        if (filter.getCursor() == null || filter.getCursor().isBlank()) {
            found = participantRepository.findFirstPageForUser(userId, filter.getType(), page);
        } else {
            ConversationCursor cursor = ConversationCursor.decode(filter.getCursor());
            found = participantRepository.findPageForUserAfter(
                    userId, cursor.activityAt(), cursor.participantId(), filter.getType(), page);
        }

        boolean hasMore = found.size() > limit;
        List<ConversationParticipant> viewers = hasMore ? found.subList(0, limit) : found;

        List<UUID> conversationIds = viewers.stream().map(viewer -> viewer.getConversation().getId()).toList();
        Map<UUID, List<ConversationParticipant>> participantsByConversation = conversationIds.isEmpty()
                ? Map.of()
                : participantRepository.findByConversationIdIn(conversationIds).stream()
                        .collect(Collectors.groupingBy(participant -> participant.getConversation().getId()));
        Map<UUID, SupportTicket> ticketsByConversation = conversationIds.isEmpty()
                ? Map.of()
                : supportTicketRepository.findByConversationIdInAndDeletedAtIsNull(conversationIds).stream()
                        .collect(Collectors.toMap(ticket -> ticket.getConversation().getId(), Function.identity()));

        List<ConversationResponse> data = viewers.stream()
                .map(viewer -> {
                    UUID conversationId = viewer.getConversation().getId();
                    return conversationMapper.toResponse(viewer,
                            participantsByConversation.getOrDefault(conversationId, List.of()),
                            supportTicketMapper.toSummary(ticketsByConversation.get(conversationId)));
                })
                .toList();

        String nextCursor = null;
        if (hasMore) {
            ConversationParticipant last = viewers.get(viewers.size() - 1);
            nextCursor = new ConversationCursor(last.getLastActivityAt(), last.getId()).encode();
        }
        return new CursorPaginated<>(data, nextCursor, hasMore);
    }

    /**
     * Get a conversation as the caller sees it.
     *
     * @param conversationId the ID of the conversation
     * @param userId the ID of the caller
     * @return the conversation
     */
    @Transactional(readOnly = true)
    public ConversationResponse getForUser(UUID conversationId, UUID userId) {
        return toResponse(requireParticipant(conversationId, userId));
    }

    /**
     * Get the private chat between the caller and another user, creating it on first use.
     *
     * @param callerId the ID of the caller
     * @param request the other user
     * @return the conversation
     */
    public ConversationResponse openPrivate(UUID callerId, CreatePrivateConversationRequest request) {
        UUID otherUserId = request.getUserId();
        if (otherUserId.equals(callerId)) {
            throw new IllegalStateException("You cannot open a chat with yourself");
        }
        if (!chatUserService.isActive(otherUserId)) {
            throw new ResourceNotFoundException("User with id=%s not found".formatted(otherUserId));
        }
        if (userBlockRepository.existsBetween(callerId, otherUserId)) {
            throw new AccessDeniedException("You cannot message this user");
        }

        String privateKey = privateKey(callerId, otherUserId);
        // Two users opening the chat at the same moment race on the unique key; the loser gets a 422
        // and finds the conversation on retry.
        Conversation conversation = conversationRepository.findByPrivateKey(privateKey)
                .orElseGet(() -> createPrivate(privateKey, callerId, otherUserId));

        return toResponse(requireParticipant(conversation.getId(), callerId));
    }

    /**
     * Mute or unmute a conversation for the caller.
     *
     * @param conversationId the ID of the conversation
     * @param userId the ID of the caller
     * @param request whether to mute, and until when
     * @return the conversation
     */
    public ConversationResponse mute(UUID conversationId, UUID userId, MuteConversationRequest request) {
        ConversationParticipant participant = requireParticipant(conversationId, userId);
        // A customer must not miss staff's replies to their ticket.
        if (participant.getConversation().getType() == ConversationType.SUPPORT
                && participant.getRole() != ParticipantRole.STAFF) {
            throw new IllegalStateException("Support chats cannot be muted");
        }
        boolean isMuted = Boolean.TRUE.equals(request.getIsMuted());
        participant.setMuted(isMuted);
        participant.setMutedUntil(isMuted ? request.getMutedUntil() : null);
        participantRepository.save(participant);

        log.info("Conversation mute changed [conversationId={}, userId={}, isMuted={}, mutedUntil={}]",
                conversationId, userId, isMuted, participant.getMutedUntil());
        return toResponse(participant);
    }

    /**
     * Mark the conversation read up to a message; marking an older message than before changes nothing.
     *
     * @param conversationId the ID of the conversation
     * @param userId the ID of the caller
     * @param request the last message read
     * @return the conversation
     */
    public ConversationResponse markRead(UUID conversationId, UUID userId, MarkConversationReadRequest request) {
        ConversationParticipant participant = requireParticipant(conversationId, userId);
        ObjectId messageId = parseMessageId(request.getMessageId());

        ChatMessage message = chatMessageRepository.findById(messageId)
                .filter(found -> found.getConversationId().equals(conversationId))
                .filter(found -> ChatAccess.isVisibleTo(found, participant))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Message with id=%s not found".formatted(request.getMessageId())));

        String current = participant.getLastReadMessageId();
        if (current != null && new ObjectId(current).compareTo(messageId) >= 0) {
            return toResponse(participant);
        }

        Instant now = now();
        participant.setLastReadMessageId(message.getId().toHexString());
        participant.setLastReadAt(now);
        participant.setUnreadCount((int) countUnreadAfter(conversationId, userId, messageId, participant.getLeftAt(),
                participant.getRole() == ParticipantRole.STAFF));
        participantRepository.save(participant);

        // The reader's own other devices get it too, so their unread badge clears.
        chatEventPublisher.publish(audienceOf(message), ChatEventType.READ_RECEIPT, conversationId,
                new ReadReceiptPayload(userId, message.getId().toHexString(), now));
        return toResponse(participant);
    }

    /**
     * Find a user's participant record, whether active or not.
     *
     * @param conversationId the ID of the conversation
     * @param userId the ID of the user
     * @return the participant
     * @throws ResourceNotFoundException if the user never took part in the conversation
     */
    @Transactional(readOnly = true)
    public ConversationParticipant requireParticipant(UUID conversationId, UUID userId) {
        return findParticipant(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conversation with id=%s not found".formatted(conversationId)));
    }

    /**
     * Find a user's participant record, whether active or not.
     *
     * @param conversationId the ID of the conversation
     * @param userId the ID of the user
     * @return the participant, or empty if the user never took part or the conversation is deleted
     */
    @Transactional(readOnly = true)
    public Optional<ConversationParticipant> findParticipant(UUID conversationId, UUID userId) {
        return participantRepository.findByConversationIdAndUserId(conversationId, userId)
                .filter(participant -> participant.getConversation().getDeletedAt() == null);
    }

    /**
     * Find a conversation that is not deleted.
     *
     * @param conversationId the ID of the conversation
     * @return the conversation
     * @throws ResourceNotFoundException if there is no such conversation
     */
    @Transactional(readOnly = true)
    public Conversation requireConversation(UUID conversationId) {
        return conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conversation with id=%s not found".formatted(conversationId)));
    }

    /**
     * Find a user's participant record and require that they have not left.
     *
     * @param conversationId the ID of the conversation
     * @param userId the ID of the user
     * @return the participant
     * @throws IllegalStateException if the user left the conversation
     */
    @Transactional(readOnly = true)
    public ConversationParticipant requireActiveParticipant(UUID conversationId, UUID userId) {
        ConversationParticipant participant = requireParticipant(conversationId, userId);
        if (!participant.isActive()) {
            throw new IllegalStateException("You are no longer a participant of this conversation");
        }
        return participant;
    }

    /**
     * Require that a user may write to a conversation: it is not read-only and nobody in it blocks them.
     *
     * @param conversation the conversation
     * @param userId the ID of the user about to write
     * @throws IllegalStateException if the conversation is read-only or a user is no longer active
     * @throws AccessDeniedException if the other user of a private chat blocks them, or they block that user
     */
    @Transactional(readOnly = true)
    public void requireWritable(Conversation conversation, UUID userId) {
        if (conversation.getReadOnlyAt() != null) {
            throw new IllegalStateException("This conversation is read-only");
        }
        if (!chatUserService.findInactive(List.of(userId)).isEmpty()) {
            throw new IllegalStateException("Your account cannot send messages");
        }
        if (conversation.getType() != ConversationType.PRIVATE) {
            return;
        }

        UUID otherUserId = participantRepository.findByConversationIdAndLeftAtIsNull(conversation.getId()).stream()
                .map(ConversationParticipant::getUserId)
                .filter(participantId -> !participantId.equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("This conversation has no one to message"));
        if (userBlockRepository.existsBetween(userId, otherUserId)) {
            throw new AccessDeniedException("You cannot message this user");
        }
        if (!chatUserService.findInactive(List.of(otherUserId)).isEmpty()) {
            throw new IllegalStateException("This user is no longer available");
        }
    }

    /**
     * Find the active participants of a conversation.
     *
     * @param conversationId the ID of the conversation
     * @return the participants who have not left
     */
    @Transactional(readOnly = true)
    public List<ConversationParticipant> activeParticipants(UUID conversationId) {
        return participantRepository.findByConversationIdAndLeftAtIsNull(conversationId);
    }

    /**
     * Find the IDs of the active participants of a conversation.
     *
     * @param conversationId the ID of the conversation
     * @return the IDs of the users who have not left
     */
    @Transactional(readOnly = true)
    public List<UUID> activeParticipantIds(UUID conversationId) {
        return activeParticipants(conversationId).stream().map(ConversationParticipant::getUserId).toList();
    }

    /**
     * Find who should receive events about a message: the active staff for an internal note, otherwise everyone active.
     *
     * @param message the message
     * @return the IDs of the users to notify
     */
    @Transactional(readOnly = true)
    public List<UUID> audienceOf(ChatMessage message) {
        return activeParticipants(message.getConversationId()).stream()
                .filter(participant -> !message.isInternalNote() || participant.getRole() == ParticipantRole.STAFF)
                .map(ConversationParticipant::getUserId)
                .toList();
    }

    /**
     * Record an internal note: it moves the staff's lists and unread counts, never the conversation's summary.
     *
     * @param conversationId the ID of the conversation
     * @param message the new note
     */
    public void recordInternalNote(UUID conversationId, ChatMessage message) {
        // Clears the persistence context: callers must reload anything they read after this.
        participantRepository.recordInternalNote(conversationId, message.getSenderId(), now());
    }

    /**
     * Make a new message the conversation's last one and count it as unread for everyone but its sender.
     *
     * @param conversation the conversation
     * @param message the new message
     */
    public void recordNewMessage(Conversation conversation, ChatMessage message) {
        applyLastMessage(conversation, message);
        conversationRepository.save(conversation);
        // Clears the persistence context: callers must reload anything they read after this.
        participantRepository.recordNewMessage(conversation.getId(), message.getSenderId(), now());
    }

    /**
     * Refresh the conversation's summary after its last message was edited or deleted.
     *
     * @param conversationId the ID of the conversation
     * @param message the edited or deleted message
     */
    public void refreshLastMessage(UUID conversationId, ChatMessage message) {
        conversationRepository.findById(conversationId)
                .filter(conversation -> message.getId().toHexString().equals(conversation.getLastMessageId()))
                .ifPresent(conversation -> {
                    applyLastMessage(conversation, message);
                    conversationRepository.save(conversation);
                });
    }

    /**
     * The text a conversation list or a push notification shows for a message.
     *
     * @param message the message
     * @return a short preview, or null if the message was deleted
     */
    public static String preview(ChatMessage message) {
        if (message.getDeletedAt() != null) {
            return null;
        }
        if (message.getBody() != null && !message.getBody().isBlank()) {
            String body = message.getBody().strip();
            return body.length() <= PREVIEW_LENGTH ? body : body.substring(0, PREVIEW_LENGTH) + "…";
        }
        List<MessageAttachment> attachments = message.getAttachments();
        if (attachments != null && !attachments.isEmpty()) {
            return attachments.size() == 1
                    ? "📎 " + truncate(attachments.get(0).fileName())
                    : "📎 %d attachments".formatted(attachments.size());
        }
        return null;
    }

    /**
     * The current time at the precision PostgreSQL stores, so that cursors compare equal after a round trip.
     *
     * @return the current time, truncated to microseconds
     */
    static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private long countUnreadAfter(UUID conversationId, UUID userId, ObjectId afterMessageId, Instant leftAt,
                                  boolean seesInternalNotes) {
        Criteria criteria = Criteria.where("conversationId").is(conversationId)
                .and("id").gt(afterMessageId)
                .and("senderId").ne(userId)
                .and("deletedAt").is(null);
        if (!seesInternalNotes) {
            criteria = criteria.and("kind").ne(MessageKind.INTERNAL_NOTE);
        }
        if (leftAt != null) {
            criteria = criteria.and("createdAt").lte(leftAt);
        }
        return mongoTemplate.count(Query.query(criteria), ChatMessage.class);
    }

    private Conversation createPrivate(String privateKey, UUID callerId, UUID otherUserId) {
        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.PRIVATE);
        conversation.setPrivateKey(privateKey);
        Conversation saved = conversationRepository.save(conversation);

        Instant now = now();
        List<ConversationParticipant> participants = new ArrayList<>();
        for (UUID userId : List.of(callerId, otherUserId)) {
            ConversationParticipant participant = new ConversationParticipant();
            participant.setConversation(saved);
            participant.setUserId(userId);
            participant.setRole(ParticipantRole.MEMBER);
            participant.setJoinedAt(now);
            participant.setLastActivityAt(now);
            participants.add(participant);
        }
        participantRepository.saveAll(participants);
        log.info("Private conversation created [id={}, userIds={}]", saved.getId(), List.of(callerId, otherUserId));

        chatEventPublisher.publish(List.of(callerId, otherUserId), ChatEventType.CONVERSATION_UPDATED,
                saved.getId(), null);
        return saved;
    }

    private ConversationResponse toResponse(ConversationParticipant viewer) {
        UUID conversationId = viewer.getConversation().getId();
        SupportTicket ticket = viewer.getConversation().getType() == ConversationType.SUPPORT
                ? supportTicketRepository.findByConversationIdAndDeletedAtIsNull(conversationId).orElse(null)
                : null;
        return conversationMapper.toResponse(viewer, participantRepository.findByConversationIdIn(List.of(conversationId)),
                supportTicketMapper.toSummary(ticket));
    }

    private static void applyLastMessage(Conversation conversation, ChatMessage message) {
        conversation.setLastMessageId(message.getId().toHexString());
        conversation.setLastMessagePreview(preview(message));
        conversation.setLastMessageSenderId(message.getSenderId());
        conversation.setLastMessageAt(message.getCreatedAt());
    }

    private static String truncate(String text) {
        return text.length() <= PREVIEW_LENGTH ? text : text.substring(0, PREVIEW_LENGTH) + "…";
    }

    private static ObjectId parseMessageId(String messageId) {
        if (messageId == null || !ObjectId.isValid(messageId)) {
            throw new ResourceNotFoundException("Message with id=%s not found".formatted(messageId));
        }
        return new ObjectId(messageId);
    }

    private static String privateKey(UUID userId, UUID otherUserId) {
        String first = userId.toString();
        String second = otherUserId.toString();
        return first.compareTo(second) < 0 ? first + ":" + second : second + ":" + first;
    }

    /**
     * Position in a user's conversation list: the activity time and participant ID of the last item.
     *
     * @param activityAt the activity time of the last item
     * @param participantId the participant ID of the last item
     */
    private record ConversationCursor(Instant activityAt, UUID participantId) {

        String encode() {
            String raw = activityAt + "|" + participantId;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        static ConversationCursor decode(String cursor) {
            try {
                String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                String[] parts = raw.split("\\|", 2);
                return new ConversationCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
            } catch (IllegalArgumentException | DateTimeParseException | ArrayIndexOutOfBoundsException e) {
                throw new InvalidFilterException("Invalid cursor");
            }
        }
    }
}
