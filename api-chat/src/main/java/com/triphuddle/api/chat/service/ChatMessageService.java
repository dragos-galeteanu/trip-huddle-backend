package com.triphuddle.api.chat.service;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.chat.ChatMessageDeletedContract;
import com.triphuddle.amqp.chat.ChatMessageDeletedEvent;
import com.triphuddle.amqp.chat.ChatMessageEditedContract;
import com.triphuddle.amqp.chat.ChatMessageEditedEvent;
import com.triphuddle.amqp.chat.ChatMessageSentContract;
import com.triphuddle.amqp.chat.ChatMessageSentEvent;
import com.triphuddle.api.chat.dto.ChatEventType;
import com.triphuddle.api.chat.dto.EditMessageRequest;
import com.triphuddle.api.chat.dto.MessageHistoryFilter;
import com.triphuddle.api.chat.dto.MessageResponse;
import com.triphuddle.api.chat.dto.SendMessageRequest;
import com.triphuddle.api.chat.entity.ChatMessage;
import com.triphuddle.api.chat.entity.Conversation;
import com.triphuddle.api.chat.entity.ConversationParticipant;
import com.triphuddle.api.chat.entity.MessageAttachment;
import com.triphuddle.api.chat.entity.ConversationType;
import com.triphuddle.api.chat.entity.MessageKind;
import com.triphuddle.api.chat.entity.MessageReaction;
import com.triphuddle.api.chat.entity.ParticipantRole;
import com.triphuddle.api.chat.entity.SystemMessageEvent;
import com.triphuddle.api.chat.mapper.ChatMessageMapper;
import com.triphuddle.api.chat.repository.ChatMessageRepository;
import com.triphuddle.api.chat.security.ChatAccess;
import com.triphuddle.common.dto.CursorPaginated;
import com.triphuddle.common.exception.InvalidFilterException;
import com.triphuddle.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sends, edits, deletes and reacts to messages, and pages through a conversation's history.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ChatMessageService {

    private static final int MAX_EMOJI_LENGTH = 32;

    private final ChatMessageRepository chatMessageRepository;
    private final MongoTemplate mongoTemplate;
    private final ConversationService conversationService;
    private final AttachmentService attachmentService;
    private final PushNotificationService pushNotificationService;
    private final ChatEventPublisher chatEventPublisher;
    private final ChatMessageMapper chatMessageMapper;
    private final RabbitMQMessageProducer messageProducer;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Get a page of a conversation's history; an ex-member only sees what was sent before they left.
     *
     * @param conversationId the ID of the conversation
     * @param userId the ID of the caller
     * @param filter the cursor and page size
     * @param isSupportStaff whether the caller is Support or Admin, who read any support chat in full
     * @return a page of messages, newest first, or oldest first when reading {@code after} a message
     */
    @Transactional(readOnly = true)
    public CursorPaginated<MessageResponse> getHistory(UUID conversationId, UUID userId, MessageHistoryFilter filter,
                                                       boolean isSupportStaff) {
        if (filter.getBefore() != null && filter.getAfter() != null) {
            throw new InvalidFilterException("Use either before or after, not both");
        }

        Optional<ConversationParticipant> participant = conversationService.findParticipant(conversationId, userId);
        boolean isStaffReader = isSupportStaff
                && conversationService.requireConversation(conversationId).getType() == ConversationType.SUPPORT;
        if (participant.isEmpty() && !isStaffReader) {
            throw new ResourceNotFoundException("Conversation with id=%s not found".formatted(conversationId));
        }
        Instant leftAt = isStaffReader ? null : participant.get().getLeftAt();
        boolean seesInternalNotes = isStaffReader;

        Criteria criteria = Criteria.where("conversationId").is(conversationId);
        if (filter.getBefore() != null) {
            criteria = criteria.and("id").lt(parseCursor(filter.getBefore()));
        }
        if (filter.getAfter() != null) {
            criteria = criteria.and("id").gt(parseCursor(filter.getAfter()));
        }
        if (leftAt != null) {
            criteria = criteria.and("createdAt").lte(leftAt);
        }
        if (!seesInternalNotes) {
            criteria = criteria.and("kind").ne(MessageKind.INTERNAL_NOTE);
        }

        Sort.Direction direction = filter.getAfter() != null ? Sort.Direction.ASC : Sort.Direction.DESC;
        Query query = Query.query(criteria)
                .with(Sort.by(direction, "id"))
                .limit(filter.getLimit() + 1);
        List<ChatMessage> found = mongoTemplate.find(query, ChatMessage.class);

        boolean hasMore = found.size() > filter.getLimit();
        List<ChatMessage> page = hasMore ? found.subList(0, filter.getLimit()) : found;
        String nextCursor = hasMore ? page.get(page.size() - 1).getId().toHexString() : null;
        return new CursorPaginated<>(chatMessageMapper.toResponseList(page), nextCursor, hasMore);
    }

    /**
     * Get a message by ID.
     *
     * @param messageId the ID of the message
     * @return the message
     */
    @Transactional(readOnly = true)
    public MessageResponse getById(String messageId) {
        return chatMessageMapper.toResponse(getEntityById(messageId));
    }

    /**
     * Get a message document by ID, deleted or not.
     *
     * @param messageId the ID of the message
     * @return the message
     * @throws ResourceNotFoundException if there is no such message
     */
    @Transactional(readOnly = true)
    public ChatMessage getEntityById(String messageId) {
        if (messageId == null || !ObjectId.isValid(messageId)) {
            throw new ResourceNotFoundException("Message with id=%s not found".formatted(messageId));
        }
        return chatMessageRepository.findById(new ObjectId(messageId))
                .orElseThrow(() -> new ResourceNotFoundException("Message with id=%s not found".formatted(messageId)));
    }

    /**
     * Send a message; sending again with the same {@code clientMessageId} returns the first message.
     *
     * @param conversationId the ID of the conversation
     * @param senderId the ID of the sender
     * @param request the content of the message
     * @return the sent message
     */
    public MessageResponse send(UUID conversationId, UUID senderId, SendMessageRequest request) {
        ConversationParticipant sender = conversationService.requireActiveParticipant(conversationId, senderId);
        Conversation conversation = sender.getConversation();
        conversationService.requireWritable(conversation, senderId);
        boolean isInternalNote = Boolean.TRUE.equals(request.getIsInternalNote());
        if (isInternalNote && sender.getRole() != ParticipantRole.STAFF) {
            throw new IllegalStateException("Only the staff of a support chat can write internal notes");
        }

        var existing = chatMessageRepository.findBySenderIdAndClientMessageId(senderId, request.getClientMessageId());
        if (existing.isPresent()) {
            return replayOf(existing.get(), conversationId);
        }

        String body = normalize(request.getBody());
        List<MessageAttachment> attachments = request.getAttachments() == null
                ? List.of()
                : request.getAttachments().stream()
                        .map(attachment -> attachmentService.resolveUploaded(conversationId, senderId, attachment))
                        .toList();
        if (body == null && attachments.isEmpty()) {
            throw new IllegalStateException("A message needs a body or at least one attachment");
        }

        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setSenderId(senderId);
        message.setClientMessageId(request.getClientMessageId());
        message.setKind(isInternalNote ? MessageKind.INTERNAL_NOTE : MessageKind.USER);
        message.setBody(body);
        message.setReplyToMessageId(resolveReplyTo(sender, request.getReplyToMessageId(), isInternalNote));
        message.setAttachments(new ArrayList<>(attachments));

        ChatMessage saved;
        try {
            // Mongo is not part of the JPA transaction: if the summary update below fails the message
            // still exists, and the next message fixes the summary.
            saved = chatMessageRepository.save(message);
        } catch (DuplicateKeyException e) {
            // A concurrent retry of the same send won the unique index; answer with its message.
            return chatMessageRepository.findBySenderIdAndClientMessageId(senderId, request.getClientMessageId())
                    .map(found -> replayOf(found, conversationId))
                    .orElseThrow(() -> e);
        }
        log.info("Chat message sent [id={}, conversationId={}, senderId={}]", saved.getId(), conversationId, senderId);

        ParticipantRole senderRole = sender.getRole();
        ConversationType conversationType = conversation.getType();
        if (isInternalNote) {
            conversationService.recordInternalNote(conversationId, saved);
        } else {
            conversationService.recordNewMessage(conversation, saved);
        }
        List<ConversationParticipant> audience = conversationService.activeParticipants(conversationId).stream()
                .filter(participant -> !isInternalNote || participant.getRole() == ParticipantRole.STAFF)
                .toList();

        MessageResponse response = chatMessageMapper.toResponse(saved);
        chatEventPublisher.publish(userIds(audience), ChatEventType.MESSAGE_CREATED, conversationId, response);
        pushNotificationService.notifyNewMessage(conversation, saved, audience, ConversationService.preview(saved));
        AfterCommit.run(() -> messageProducer.publish(
                new ChatMessageSentEvent(saved.getId().toHexString(), conversationId, senderId),
                "",
                ChatMessageSentContract.CHAT_MESSAGE_SENT_QUEUE_NAME
        ));
        // Last, so that whatever a listener changes in reply reaches clients after the message itself.
        applicationEventPublisher.publishEvent(new ChatMessageSentInternally(saved, conversationType, senderRole));
        return response;
    }

    /**
     * Post a system message recording a change to the conversation; it is nobody's to edit or delete.
     *
     * @param conversationId the ID of the conversation
     * @param actorId who made the change, or null if the system did
     * @param event what changed
     * @param body the English text shown by clients that do not render the event themselves
     * @return the posted message
     */
    public MessageResponse postSystemMessage(UUID conversationId, UUID actorId, SystemMessageEvent event, String body) {
        Conversation conversation = conversationService.requireConversation(conversationId);

        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setSenderId(actorId);
        // The unique index on sender and clientMessageId needs a value no client will send.
        message.setClientMessageId("system:" + UUID.randomUUID());
        message.setKind(MessageKind.SYSTEM);
        message.setBody(body);
        message.setSystemEvent(event);
        ChatMessage saved = chatMessageRepository.save(message);
        log.info("System message posted [id={}, conversationId={}, type={}]",
                saved.getId(), conversationId, event.type());

        conversationService.recordNewMessage(conversation, saved);
        MessageResponse response = chatMessageMapper.toResponse(saved);
        chatEventPublisher.publish(conversationService.audienceOf(saved), ChatEventType.MESSAGE_CREATED,
                conversationId, response);
        return response;
    }

    /**
     * Replace the text of the caller's own message; its attachments stay as they are.
     *
     * @param messageId the ID of the message
     * @param userId the ID of the caller
     * @param request the new text
     * @return the edited message
     */
    public MessageResponse edit(String messageId, UUID userId, EditMessageRequest request) {
        ChatMessage message = getEntityById(messageId);
        if (message.getDeletedAt() != null) {
            throw new IllegalStateException("A deleted message cannot be edited");
        }
        if (message.getKind() == MessageKind.SYSTEM) {
            throw new IllegalStateException("A system message cannot be edited");
        }
        if (!message.getSenderId().equals(userId)) {
            throw new AccessDeniedException("Only the sender can edit a message");
        }

        ConversationParticipant participant =
                conversationService.requireActiveParticipant(message.getConversationId(), userId);
        conversationService.requireWritable(participant.getConversation(), userId);

        message.setBody(normalize(request.getBody()));
        message.setEditedAt(Instant.now());
        ChatMessage saved = chatMessageRepository.save(message);
        log.info("Chat message edited [id={}, conversationId={}]", saved.getId(), saved.getConversationId());

        conversationService.refreshLastMessage(saved.getConversationId(), saved);
        MessageResponse response = chatMessageMapper.toResponse(saved);
        chatEventPublisher.publish(conversationService.audienceOf(saved),
                ChatEventType.MESSAGE_UPDATED, saved.getConversationId(), response);
        AfterCommit.run(() -> messageProducer.publish(
                new ChatMessageEditedEvent(saved.getId().toHexString(), saved.getConversationId(), userId),
                "",
                ChatMessageEditedContract.CHAT_MESSAGE_EDITED_QUEUE_NAME
        ));
        return response;
    }

    /**
     * Delete a message, as its sender or a group Admin; the policy has already decided who may.
     *
     * @param messageId the ID of the message
     * @param userId the ID of the caller
     * @return the deleted message, without its content
     */
    public MessageResponse delete(String messageId, UUID userId) {
        ChatMessage message = getEntityById(messageId);
        if (message.getKind() == MessageKind.SYSTEM) {
            throw new IllegalStateException("A system message cannot be deleted");
        }
        return softDelete(message, userId);
    }

    /**
     * Delete a message as staff acting on a report, whether or not they take part in the conversation.
     *
     * @param messageId the ID of the message
     * @param staffUserId the ID of the staff member
     */
    public void deleteAsModerator(String messageId, UUID staffUserId) {
        ChatMessage message = getEntityById(messageId);
        if (message.getDeletedAt() == null) {
            softDelete(message, staffUserId);
        }
    }

    /**
     * Add the caller's reaction to a message; reacting twice with the same emoji changes nothing.
     *
     * @param messageId the ID of the message
     * @param userId the ID of the caller
     * @param emoji the emoji
     * @return the message with its reactions
     */
    public MessageResponse addReaction(String messageId, UUID userId, String emoji) {
        ChatMessage message = requireReactable(messageId, userId);
        Update update = new Update().addToSet("reactions", new MessageReaction(normalizeEmoji(emoji), userId));
        return applyReactionUpdate(message, update);
    }

    /**
     * Remove the caller's reaction to a message; removing one that is not there changes nothing.
     *
     * @param messageId the ID of the message
     * @param userId the ID of the caller
     * @param emoji the emoji
     * @return the message with its reactions
     */
    public MessageResponse removeReaction(String messageId, UUID userId, String emoji) {
        ChatMessage message = requireReactable(messageId, userId);
        Update update = new Update().pull("reactions", new MessageReaction(normalizeEmoji(emoji), userId));
        return applyReactionUpdate(message, update);
    }

    /**
     * Find the participant-written message the customer could see right before a message, ignoring notes and system ones.
     *
     * @param message the message
     * @return the previous visible USER message, or empty if there is none
     */
    @Transactional(readOnly = true)
    public Optional<ChatMessage> findPreviousUserMessage(ChatMessage message) {
        // A message stored before kinds existed has none, and is a USER message.
        Criteria criteria = Criteria.where("conversationId").is(message.getConversationId())
                .and("id").lt(message.getId())
                .and("deletedAt").is(null)
                .and("kind").nin(MessageKind.SYSTEM, MessageKind.INTERNAL_NOTE);
        Query query = Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "id")).limit(1);
        return Optional.ofNullable(mongoTemplate.findOne(query, ChatMessage.class));
    }

    /**
     * Get the messages around a message, oldest first, with deleted content shown, for staff reviewing it.
     *
     * @param message the message at the centre
     * @param around how many messages to take on each side
     * @return the messages, including the centre one
     */
    @Transactional(readOnly = true)
    public List<MessageResponse> getModerationContext(ChatMessage message, int around) {
        Criteria sameConversation = Criteria.where("conversationId").is(message.getConversationId());

        List<ChatMessage> before = new ArrayList<>(mongoTemplate.find(
                Query.query(Criteria.where("id").lt(message.getId()).andOperator(sameConversation))
                        .with(Sort.by(Sort.Direction.DESC, "id")).limit(around),
                ChatMessage.class));
        Collections.reverse(before);
        List<ChatMessage> after = mongoTemplate.find(
                Query.query(Criteria.where("id").gt(message.getId()).andOperator(sameConversation))
                        .with(Sort.by(Sort.Direction.ASC, "id")).limit(around),
                ChatMessage.class);

        List<ChatMessage> context = new ArrayList<>(before);
        context.add(message);
        context.addAll(after);
        return context.stream().map(chatMessageMapper::toModerationResponse).toList();
    }

    private MessageResponse softDelete(ChatMessage message, UUID deletedByUserId) {
        if (message.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Message with id=%s not found".formatted(message.getId()));
        }
        // The content is kept, only hidden, so that staff can still review a report on it.
        message.setDeletedAt(Instant.now());
        message.setDeletedByUserId(deletedByUserId);
        ChatMessage saved = chatMessageRepository.save(message);
        log.info("Chat message deleted [id={}, conversationId={}, deletedBy={}]",
                saved.getId(), saved.getConversationId(), deletedByUserId);

        conversationService.refreshLastMessage(saved.getConversationId(), saved);
        MessageResponse response = chatMessageMapper.toResponse(saved);
        chatEventPublisher.publish(conversationService.audienceOf(saved),
                ChatEventType.MESSAGE_DELETED, saved.getConversationId(), response);
        AfterCommit.run(() -> messageProducer.publish(
                new ChatMessageDeletedEvent(saved.getId().toHexString(), saved.getConversationId(), deletedByUserId),
                "",
                ChatMessageDeletedContract.CHAT_MESSAGE_DELETED_QUEUE_NAME
        ));
        return response;
    }

    private ChatMessage requireReactable(String messageId, UUID userId) {
        ChatMessage message = getEntityById(messageId);
        if (message.getDeletedAt() != null) {
            throw new IllegalStateException("You cannot react to a deleted message");
        }
        if (message.getKind() == MessageKind.SYSTEM) {
            throw new IllegalStateException("You cannot react to a system message");
        }
        ConversationParticipant participant =
                conversationService.requireActiveParticipant(message.getConversationId(), userId);
        conversationService.requireWritable(participant.getConversation(), userId);
        return message;
    }

    private MessageResponse applyReactionUpdate(ChatMessage message, Update update) {
        // An atomic update, so that two users reacting at once do not overwrite each other.
        Query query = Query.query(Criteria.where("id").is(message.getId()).and("deletedAt").is(null));
        ChatMessage updated = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), ChatMessage.class);
        if (updated == null) {
            throw new IllegalStateException("You cannot react to a deleted message");
        }

        MessageResponse response = chatMessageMapper.toResponse(updated);
        chatEventPublisher.publish(conversationService.audienceOf(updated),
                ChatEventType.REACTIONS_UPDATED, updated.getConversationId(), response);
        return response;
    }

    private MessageResponse replayOf(ChatMessage existing, UUID conversationId) {
        if (!existing.getConversationId().equals(conversationId)) {
            throw new IllegalStateException("clientMessageId was already used for another conversation");
        }
        log.info("Chat message send replayed [id={}, clientMessageId={}]", existing.getId(), existing.getClientMessageId());
        return chatMessageMapper.toResponse(existing);
    }

    private ObjectId resolveReplyTo(ConversationParticipant sender, String replyToMessageId, boolean isInternalNote) {
        if (replyToMessageId == null || replyToMessageId.isBlank()) {
            return null;
        }
        ChatMessage replyTo = getEntityById(replyToMessageId);
        if (!replyTo.getConversationId().equals(sender.getConversation().getId())
                || !ChatAccess.isVisibleTo(replyTo, sender)) {
            throw new ResourceNotFoundException("Message with id=%s not found".formatted(replyToMessageId));
        }
        // A reply the customer sees must not point them at a note they cannot open.
        if (replyTo.isInternalNote() && !isInternalNote) {
            throw new IllegalStateException("Only an internal note can reply to an internal note");
        }
        return replyTo.getId();
    }

    private static String normalize(String body) {
        if (body == null) {
            return null;
        }
        String stripped = body.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private static String normalizeEmoji(String emoji) {
        String stripped = emoji == null ? "" : emoji.strip();
        if (stripped.isEmpty() || stripped.length() > MAX_EMOJI_LENGTH) {
            throw new IllegalStateException("A reaction must be a single emoji");
        }
        return stripped;
    }

    private static ObjectId parseCursor(String cursor) {
        if (!ObjectId.isValid(cursor)) {
            throw new InvalidFilterException("Invalid cursor");
        }
        return new ObjectId(cursor);
    }

    private static List<UUID> userIds(List<ConversationParticipant> participants) {
        return participants.stream().map(ConversationParticipant::getUserId).toList();
    }
}
