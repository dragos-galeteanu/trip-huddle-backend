package com.triphuddle.api.chat.service;

import com.triphuddle.common.filter.CursorPaginationFilter;
import com.triphuddle.common.dto.PageCursor;
import com.triphuddle.common.dto.CursorPaginated;
import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.chat.SupportTicketCreatedContract;
import com.triphuddle.amqp.chat.SupportTicketCreatedEvent;
import com.triphuddle.amqp.chat.SupportTicketRepliedContract;
import com.triphuddle.amqp.chat.SupportTicketRepliedEvent;
import com.triphuddle.amqp.chat.SupportTicketStatusChangedContract;
import com.triphuddle.amqp.chat.SupportTicketStatusChangedEvent;
import com.triphuddle.api.chat.config.ChatProperties;
import com.triphuddle.api.chat.config.ChatStompAuthChannelInterceptor;
import com.triphuddle.api.chat.dto.ChangeTicketStatusRequest;
import com.triphuddle.api.chat.dto.ChatEventType;
import com.triphuddle.api.chat.dto.CreateSupportTicketRequest;
import com.triphuddle.api.chat.dto.RateSupportTicketRequest;
import com.triphuddle.api.chat.dto.SendMessageRequest;
import com.triphuddle.api.chat.dto.SupportTicketEventResponse;
import com.triphuddle.api.chat.dto.SupportTicketQueueFilter;
import com.triphuddle.api.chat.dto.SupportTicketResponse;
import com.triphuddle.api.chat.dto.UpdateSupportTicketRequest;
import com.triphuddle.api.chat.entity.ChatMessage;
import com.triphuddle.api.chat.entity.Conversation;
import com.triphuddle.api.chat.entity.ConversationParticipant;
import com.triphuddle.api.chat.entity.ConversationType;
import com.triphuddle.api.chat.entity.MessageKind;
import com.triphuddle.api.chat.entity.ParticipantRole;
import com.triphuddle.api.chat.entity.SupportTicket;
import com.triphuddle.api.chat.entity.SupportTicketEvent;
import com.triphuddle.api.chat.entity.SupportTicketEventType;
import com.triphuddle.api.chat.entity.SystemMessageEvent;
import com.triphuddle.api.chat.entity.TicketCategory;
import com.triphuddle.api.chat.entity.TicketPriority;
import com.triphuddle.api.chat.entity.TicketStatus;
import com.triphuddle.api.chat.mapper.SupportTicketMapper;
import com.triphuddle.api.chat.repository.ChatMessageRepository;
import com.triphuddle.api.chat.repository.ConversationParticipantRepository;
import com.triphuddle.api.chat.repository.ConversationRepository;
import com.triphuddle.api.chat.repository.MessageReportRepository;
import com.triphuddle.api.chat.repository.SupportTicketEventRepository;
import com.triphuddle.api.chat.repository.SupportTicketRepository;
import com.triphuddle.api.chat.security.ChatAccess;
import com.triphuddle.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Opens support tickets and moves them through their lifecycle, recording each change and posting it to the chat.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SupportTicketService {

    private final SupportTicketRepository supportTicketRepository;
    private final SupportTicketEventRepository supportTicketEventRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MessageReportRepository messageReportRepository;
    private final ConversationService conversationService;
    private final ChatMessageService chatMessageService;
    private final ChatUserService chatUserService;
    private final ChatEventPublisher chatEventPublisher;
    private final SupportTicketMapper supportTicketMapper;
    private final RabbitMQMessageProducer messageProducer;
    private final ChatProperties chatProperties;

    /**
     * Open a ticket with its first message; a retry with the same {@code clientMessageId} returns the same ticket.
     *
     * @param callerId the ID of the caller
     * @param request the ticket and its first message; {@code userId} only when staff open it towards a user
     * @return the opened ticket
     */
    public SupportTicketResponse open(UUID callerId, CreateSupportTicketRequest request) {
        boolean isOutbound = request.getUserId() != null;

        Optional<ChatMessage> replayed =
                chatMessageRepository.findBySenderIdAndClientMessageId(callerId, request.getClientMessageId());
        if (replayed.isPresent()) {
            SupportTicket ticket = supportTicketRepository
                    .findByConversationIdAndDeletedAtIsNull(replayed.get().getConversationId())
                    .orElseThrow(() -> new IllegalStateException(
                            "clientMessageId was already used for another conversation"));
            log.info("Support ticket open replayed [id={}, clientMessageId={}]",
                    ticket.getId(), request.getClientMessageId());
            return toResponse(ticket, isOutbound);
        }

        UUID requesterId = isOutbound ? request.getUserId() : callerId;
        if (!chatUserService.isActive(callerId)) {
            throw new IllegalStateException("Your account cannot open support tickets");
        }
        if (isOutbound) {
            if (requesterId.equals(callerId)) {
                throw new IllegalStateException("Open a ticket towards another user");
            }
            if (!chatUserService.isActive(requesterId)) {
                throw new ResourceNotFoundException("User with id=%s not found".formatted(requesterId));
            }
        } else {
            int limit = chatProperties.support().maxOngoingTicketsPerCustomer();
            if (supportTicketRepository.countOpenedByRequesterInStatuses(callerId, TicketStatus.ONGOING) >= limit) {
                throw new IllegalStateException("You can have at most %d open support tickets".formatted(limit));
            }
        }
        validateLinks(request, requesterId, isOutbound);

        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.SUPPORT);
        conversation.setName(request.getSubject().strip());
        Conversation savedConversation = conversationRepository.save(conversation);

        Instant now = ConversationService.now();
        List<ConversationParticipant> participants = new ArrayList<>();
        participants.add(newParticipant(savedConversation, requesterId, ParticipantRole.MEMBER, now));
        if (isOutbound) {
            participants.add(newParticipant(savedConversation, callerId, ParticipantRole.STAFF, now));
        }
        participantRepository.saveAll(participants);

        SupportTicket ticket = new SupportTicket();
        ticket.setConversation(savedConversation);
        ticket.setNumber(supportTicketRepository.nextNumber());
        ticket.setRequesterId(requesterId);
        ticket.setOpenedByUserId(callerId);
        ticket.setSubject(request.getSubject().strip());
        ticket.setCategory(request.getCategory());
        ticket.setPriority(request.getCategory() == TicketCategory.SAFETY ? TicketPriority.HIGH : TicketPriority.NORMAL);
        // A ticket staff open towards a user is theirs from the start.
        ticket.setAssigneeId(isOutbound ? callerId : null);
        ticket.setStatus(isOutbound ? TicketStatus.IN_PROGRESS : TicketStatus.OPEN);
        ticket.setGroupId(request.getGroupId());
        ticket.setLinkedConversationId(request.getConversationId());
        ticket.setMessageId(request.getMessageId());
        ticket.setMessageReportId(request.getMessageReportId());

        List<Change> changes = new ArrayList<>();
        changes.add(new Change(SupportTicketEventType.CREATED, null, null, ticket.getStatus().name()));
        if (isOutbound) {
            changes.add(new Change(SupportTicketEventType.STAFF_JOINED, callerId, null, null));
            changes.add(new Change(SupportTicketEventType.ASSIGNED, callerId, null, null));
        }
        UUID ticketId = commit(ticket, callerId, changes);
        log.info("Support ticket opened [id={}, requesterId={}, openedBy={}, category={}]",
                ticketId, requesterId, callerId, request.getCategory());

        SendMessageRequest firstMessage = new SendMessageRequest();
        firstMessage.setClientMessageId(request.getClientMessageId());
        firstMessage.setBody(request.getBody());
        chatMessageService.send(savedConversation.getId(), callerId, firstMessage);

        return toResponse(requireTicket(ticketId), isOutbound);
    }

    /**
     * Get a customer's own tickets, newest first.
     *
     * @param requesterId the ID of the customer
     * @param status the status to keep, or null for all
     * @param filter the cursor and page size
     * @return a page of tickets
     */
    @Transactional(readOnly = true)
    public CursorPaginated<SupportTicketResponse> getMine(UUID requesterId, TicketStatus status,
                                                          CursorPaginationFilter filter) {
        PageCursor cursor = PageCursor.decode(filter.getCursor(), 2);
        boolean isFirst = cursor == null;
        List<SupportTicket> found = supportTicketRepository.findForRequester(requesterId, status, isFirst,
                isFirst ? PageCursor.FIRST_INSTANT : cursor.instantAt(0),
                isFirst ? PageCursor.FIRST_ID : cursor.uuidAt(1), filter.toLimit());
        return CursorPaginated.of(found, filter.getLimit(), ticket -> PageCursor.of(ticket.getCreatedAt(),
                ticket.getId()), tickets -> toResponses(tickets, false));
    }

    /**
     * Get the staff queue, most urgent first and then oldest first.
     *
     * @param filter the cursor, page size and filters
     * @return a page of tickets
     */
    @Transactional(readOnly = true)
    public CursorPaginated<SupportTicketResponse> getQueue(SupportTicketQueueFilter filter) {
        PageCursor cursor = PageCursor.decode(filter.getCursor(), 3);
        boolean isFirst = cursor == null;
        List<SupportTicket> found = supportTicketRepository.findQueue(filter.getStatus(), filter.getCategory(),
                filter.getPriority(), filter.getAssigneeId(), Boolean.TRUE.equals(filter.getIsUnassigned()),
                isFirst, isFirst ? 0 : cursor.intAt(0), isFirst ? PageCursor.FIRST_INSTANT : cursor.instantAt(1),
                isFirst ? PageCursor.FIRST_ID : cursor.uuidAt(2), filter.toLimit());
        return CursorPaginated.of(found, filter.getLimit(), ticket -> PageCursor.of(urgencyRank(ticket.getPriority()),
                ticket.getCreatedAt(), ticket.getId()), tickets -> toResponses(tickets, true));
    }

    // The queue's order: 0 for URGENT down to 3 for LOW, as the queue query ranks them.
    private static int urgencyRank(TicketPriority priority) {
        return switch (priority) {
            case URGENT -> 0;
            case HIGH -> 1;
            case NORMAL -> 2;
            case LOW -> 3;
        };
    }

    /**
     * Get a ticket.
     *
     * @param id the ID of the ticket
     * @param isStaffView whether staff are looking at it
     * @return the ticket
     */
    @Transactional(readOnly = true)
    public SupportTicketResponse getById(UUID id, boolean isStaffView) {
        return toResponse(requireTicket(id), isStaffView);
    }

    /**
     * Get a ticket's history, oldest first.
     *
     * @param id the ID of the ticket
     * @return the events of the ticket
     */
    @Transactional(readOnly = true)
    public List<SupportTicketEventResponse> getEvents(UUID id) {
        SupportTicket ticket = requireTicket(id);
        return supportTicketMapper.toEventResponseList(
                supportTicketEventRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId()));
    }

    /**
     * Change a ticket's priority or category, as staff.
     *
     * @param id the ID of the ticket
     * @param staffId the ID of the staff member
     * @param request the new priority and category; a missing one stays as it is
     * @return the ticket
     */
    public SupportTicketResponse update(UUID id, UUID staffId, UpdateSupportTicketRequest request) {
        SupportTicket ticket = requireTicket(id);
        List<Change> changes = new ArrayList<>();
        if (request.getPriority() != null && request.getPriority() != ticket.getPriority()) {
            changes.add(new Change(SupportTicketEventType.PRIORITY_CHANGED, null,
                    ticket.getPriority().name(), request.getPriority().name()));
            ticket.setPriority(request.getPriority());
        }
        if (request.getCategory() != null && request.getCategory() != ticket.getCategory()) {
            changes.add(new Change(SupportTicketEventType.CATEGORY_CHANGED, null,
                    ticket.getCategory().name(), request.getCategory().name()));
            ticket.setCategory(request.getCategory());
        }
        if (changes.isEmpty()) {
            return toResponse(ticket, true);
        }
        return toResponse(requireTicket(commit(ticket, staffId, changes)), true);
    }

    /**
     * Move a ticket to another status; the customer may only close it, or reopen it while resolved.
     *
     * @param id the ID of the ticket
     * @param callerId the ID of the caller
     * @param isStaff whether the caller acts as staff
     * @param request the status to move to; OPEN or IN_PROGRESS on a finished ticket reopens it
     * @return the ticket
     */
    public SupportTicketResponse changeStatus(UUID id, UUID callerId, boolean isStaff,
                                              ChangeTicketStatusRequest request) {
        SupportTicket ticket = requireTicket(id);
        TicketStatus current = ticket.getStatus();
        TicketStatus target = request.getStatus();

        boolean isReopen = (target == TicketStatus.OPEN || target == TicketStatus.IN_PROGRESS)
                && (current == TicketStatus.RESOLVED || current == TicketStatus.CLOSED);
        if (isReopen) {
            target = reopenStatus(ticket);
        }
        if (target == current) {
            return toResponse(ticket, isStaff);
        }

        if (isStaff) {
            if (!current.canStaffMoveTo(target)) {
                throw new IllegalStateException("A ticket cannot move from %s to %s".formatted(current, target));
            }
            if ((target == TicketStatus.IN_PROGRESS || target == TicketStatus.WAITING_ON_CUSTOMER)
                    && ticket.getAssigneeId() == null) {
                throw new IllegalStateException("Claim the ticket before working on it");
            }
        } else if (target != TicketStatus.CLOSED && !(isReopen && current == TicketStatus.RESOLVED)) {
            throw new IllegalStateException("You can close your ticket, or reopen it while it is resolved");
        }

        List<Change> changes = List.of(applyStatus(ticket, target, callerId));
        return toResponse(requireTicket(commit(ticket, callerId, changes)), isStaff);
    }

    /**
     * Take an unassigned ticket, joining its chat; an open ticket moves to IN_PROGRESS.
     *
     * @param id the ID of the ticket
     * @param staffId the ID of the staff member
     * @return the ticket
     */
    public SupportTicketResponse claim(UUID id, UUID staffId) {
        SupportTicket ticket = requireTicket(id);
        requireHandleable(ticket, staffId);
        if (staffId.equals(ticket.getAssigneeId())) {
            return toResponse(ticket, true);
        }
        if (ticket.getAssigneeId() != null) {
            throw new IllegalStateException(
                    "Ticket %s is already assigned; join it to help instead".formatted(ticket.getReference()));
        }

        List<Change> changes = new ArrayList<>();
        joinAsStaff(ticket, staffId).ifPresent(changes::add);
        ticket.setAssigneeId(staffId);
        changes.add(new Change(SupportTicketEventType.ASSIGNED, staffId, null, null));
        if (ticket.getStatus() == TicketStatus.OPEN) {
            changes.add(applyStatus(ticket, TicketStatus.IN_PROGRESS, staffId));
        }
        return toResponse(requireTicket(commit(ticket, staffId, changes)), true);
    }

    /**
     * Join a ticket's chat to help, without becoming its assignee.
     *
     * @param id the ID of the ticket
     * @param staffId the ID of the staff member
     * @return the ticket
     */
    public SupportTicketResponse join(UUID id, UUID staffId) {
        SupportTicket ticket = requireTicket(id);
        requireHandleable(ticket, staffId);
        Optional<Change> joined = joinAsStaff(ticket, staffId);
        if (joined.isEmpty()) {
            return toResponse(ticket, true);
        }
        return toResponse(requireTicket(commit(ticket, staffId, List.of(joined.get()))), true);
    }

    /**
     * Leave a ticket's chat; the assignee leaving puts an unfinished ticket back in the queue.
     *
     * @param id the ID of the ticket
     * @param staffId the ID of the staff member
     * @return the ticket
     */
    public SupportTicketResponse leave(UUID id, UUID staffId) {
        SupportTicket ticket = requireTicket(id);
        ConversationParticipant participant = participantRepository
                .findByConversationIdAndUserId(ticket.getConversation().getId(), staffId)
                .filter(ConversationParticipant::isActive)
                .filter(found -> found.getRole() == ParticipantRole.STAFF)
                .orElseThrow(() -> new IllegalStateException("You are not on this ticket"));
        participant.setLeftAt(ConversationService.now());
        participantRepository.save(participant);

        List<Change> changes = new ArrayList<>();
        if (staffId.equals(ticket.getAssigneeId())) {
            changes.addAll(unassign(ticket, staffId));
        }
        changes.add(new Change(SupportTicketEventType.STAFF_LEFT, staffId, null, null));
        return toResponse(requireTicket(commit(ticket, staffId, changes)), true);
    }

    /**
     * Give up a ticket's assignment, putting an unfinished ticket back in the queue; the policy decided who may.
     *
     * @param id the ID of the ticket
     * @param staffId the ID of the staff member releasing it
     * @return the ticket
     */
    public SupportTicketResponse release(UUID id, UUID staffId) {
        SupportTicket ticket = requireTicket(id);
        if (ticket.getAssigneeId() == null) {
            throw new IllegalStateException("Ticket %s is not assigned".formatted(ticket.getReference()));
        }
        return toResponse(requireTicket(commit(ticket, staffId, unassign(ticket, staffId))), true);
    }

    /**
     * Rate the support of a resolved or closed ticket, as its customer; rating again replaces the rating.
     *
     * @param id the ID of the ticket
     * @param requesterId the ID of the customer
     * @param request the rating and an optional comment
     * @return the ticket
     */
    public SupportTicketResponse rate(UUID id, UUID requesterId, RateSupportTicketRequest request) {
        SupportTicket ticket = requireTicket(id);
        if (ticket.getStatus() != TicketStatus.RESOLVED && ticket.getStatus() != TicketStatus.CLOSED) {
            throw new IllegalStateException("A ticket can be rated once it is resolved or closed");
        }
        String previous = ticket.getRating() == null ? null : ticket.getRating().toString();
        String comment = request.getComment() == null || request.getComment().isBlank()
                ? null
                : request.getComment().strip();
        ticket.setRating(request.getRating());
        ticket.setRatingComment(comment);
        ticket.setRatedAt(Instant.now());

        List<Change> changes = List.of(
                new Change(SupportTicketEventType.RATED, null, previous, request.getRating().toString()));
        return toResponse(requireTicket(commit(ticket, requesterId, changes)), false);
    }

    /**
     * Close a batch of tickets that stayed RESOLVED past the cutoff; the system is the actor.
     *
     * @param cutoff the latest resolution time to close
     * @param batchSize the most tickets to close
     * @return how many tickets were closed
     */
    public int autoCloseResolved(Instant cutoff, int batchSize) {
        List<UUID> dueIds = supportTicketRepository.lockDueForAutoClose(cutoff, PageRequest.of(0, batchSize))
                .stream().map(SupportTicket::getId).toList();
        int closed = 0;
        for (UUID id : dueIds) {
            // Each close clears the persistence context, so every ticket is read again; the row lock stays.
            SupportTicket ticket = requireTicket(id);
            if (ticket.getStatus() == TicketStatus.RESOLVED) {
                commit(ticket, null, List.of(applyStatus(ticket, TicketStatus.CLOSED, null)));
                closed++;
            }
        }
        return closed;
    }

    /**
     * Follow messages in support chats: staff's first reply is recorded, and a customer's reply reopens the ticket.
     *
     * @param event the message just sent
     */
    @EventListener
    public void onMessageSent(ChatMessageSentInternally event) {
        ChatMessage message = event.message();
        if (event.conversationType() != ConversationType.SUPPORT || message.getKind() != MessageKind.USER) {
            return;
        }
        SupportTicket ticket = supportTicketRepository
                .findByConversationIdAndDeletedAtIsNull(message.getConversationId())
                .orElse(null);
        if (ticket == null) {
            return;
        }

        if (event.senderRole() == ParticipantRole.STAFF) {
            // Response time only means something for tickets the customer opened.
            if (ticket.getFirstResponseAt() == null && ticket.getOpenedByUserId().equals(ticket.getRequesterId())) {
                ticket.setFirstResponseAt(message.getCreatedAt() != null ? message.getCreatedAt() : Instant.now());
                supportTicketRepository.save(ticket);
            }
            publishRepliedIfFirstSinceCustomer(ticket, message);
            return;
        }

        boolean isAwaitingCustomer = ticket.getStatus() == TicketStatus.WAITING_ON_CUSTOMER
                || ticket.getStatus() == TicketStatus.RESOLVED;
        if (message.getSenderId().equals(ticket.getRequesterId()) && isAwaitingCustomer) {
            UUID requesterId = ticket.getRequesterId();
            commit(ticket, requesterId, List.of(applyStatus(ticket, reopenStatus(ticket), requesterId)));
        }
    }

    private void publishRepliedIfFirstSinceCustomer(SupportTicket ticket, ChatMessage reply) {
        // One email per round of replies: only the first staff message after the customer last wrote counts.
        boolean isAfterCustomer = chatMessageService.findPreviousUserMessage(reply)
                .map(previous -> ticket.getRequesterId().equals(previous.getSenderId()))
                .orElse(false);
        if (!isAfterCustomer) {
            return;
        }
        SupportTicketRepliedEvent replied = new SupportTicketRepliedEvent(ticket.getId(), ticket.getReference(),
                reply.getConversationId(), ticket.getRequesterId(), reply.getSenderId(), reply.getId().toHexString(),
                ticket.getSubject());
        AfterCommit.run(() -> messageProducer.publish(
                replied, "", SupportTicketRepliedContract.SUPPORT_TICKET_REPLIED_QUEUE_NAME));
    }

    private void validateLinks(CreateSupportTicketRequest request, UUID requesterId, boolean isOutbound) {
        // A customer may only point at what they can see; staff may point at anything that exists.
        if (request.getGroupId() != null) {
            conversationRepository.findByGroupId(request.getGroupId())
                    .filter(conversation -> conversation.getDeletedAt() == null)
                    .filter(conversation -> isOutbound || hasTakenPart(conversation.getId(), requesterId))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Group with id=%s not found".formatted(request.getGroupId())));
        }
        if (request.getConversationId() != null) {
            conversationRepository.findByIdAndDeletedAtIsNull(request.getConversationId())
                    .filter(conversation -> isOutbound || hasTakenPart(conversation.getId(), requesterId))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Conversation with id=%s not found".formatted(request.getConversationId())));
        }
        if (request.getMessageId() != null) {
            ChatMessage message = chatMessageService.getEntityById(request.getMessageId());
            boolean isVisible = isOutbound || participantRepository
                    .findByConversationIdAndUserId(message.getConversationId(), requesterId)
                    .filter(participant -> ChatAccess.isVisibleTo(message, participant))
                    .isPresent();
            if (!isVisible) {
                throw new ResourceNotFoundException("Message with id=%s not found".formatted(request.getMessageId()));
            }
        }
        if (request.getMessageReportId() != null) {
            messageReportRepository.findByIdAndDeletedAtIsNull(request.getMessageReportId())
                    .filter(report -> isOutbound || report.getReporterId().equals(requesterId))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Message report with id=%s not found".formatted(request.getMessageReportId())));
        }
    }

    private boolean hasTakenPart(UUID conversationId, UUID userId) {
        return participantRepository.findByConversationIdAndUserId(conversationId, userId).isPresent();
    }

    private void requireHandleable(SupportTicket ticket, UUID staffId) {
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new IllegalStateException("Reopen ticket %s first".formatted(ticket.getReference()));
        }
        if (staffId.equals(ticket.getRequesterId())) {
            throw new IllegalStateException("You cannot handle your own ticket");
        }
    }

    private Optional<Change> joinAsStaff(SupportTicket ticket, UUID staffId) {
        Conversation conversation = ticket.getConversation();
        Optional<ConversationParticipant> existing =
                participantRepository.findByConversationIdAndUserId(conversation.getId(), staffId);
        if (existing.isPresent() && existing.get().isActive()) {
            return Optional.empty();
        }

        // Someone who left and comes back keeps their record, and with it what they had read.
        Instant now = ConversationService.now();
        ConversationParticipant participant =
                existing.orElseGet(() -> newParticipant(conversation, staffId, ParticipantRole.STAFF, now));
        participant.setRole(ParticipantRole.STAFF);
        participant.setLeftAt(null);
        participant.setLastActivityAt(now);
        participantRepository.save(participant);
        return Optional.of(new Change(SupportTicketEventType.STAFF_JOINED, staffId, null, null));
    }

    private List<Change> unassign(SupportTicket ticket, UUID actorId) {
        List<Change> changes = new ArrayList<>();
        UUID former = ticket.getAssigneeId();
        ticket.setAssigneeId(null);
        changes.add(new Change(SupportTicketEventType.UNASSIGNED, former, null, null));
        if (ticket.getStatus() == TicketStatus.IN_PROGRESS || ticket.getStatus() == TicketStatus.WAITING_ON_CUSTOMER) {
            changes.add(applyStatus(ticket, TicketStatus.OPEN, actorId));
        }
        return changes;
    }

    private Change applyStatus(SupportTicket ticket, TicketStatus target, UUID actorId) {
        TicketStatus from = ticket.getStatus();
        Instant now = ConversationService.now();
        Conversation conversation = ticket.getConversation();
        ticket.setStatus(target);

        if (target == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(now);
        }
        if (target == TicketStatus.CLOSED) {
            ticket.setClosedAt(now);
            ticket.setClosedByUserId(actorId);
            conversation.setReadOnlyAt(now);
            conversationRepository.save(conversation);
        } else if (from == TicketStatus.CLOSED) {
            ticket.setClosedAt(null);
            ticket.setClosedByUserId(null);
            conversation.setReadOnlyAt(null);
            conversationRepository.save(conversation);
        }
        return new Change(SupportTicketEventType.STATUS_CHANGED, null, from.name(), target.name());
    }

    private static TicketStatus reopenStatus(SupportTicket ticket) {
        return ticket.getAssigneeId() != null ? TicketStatus.IN_PROGRESS : TicketStatus.OPEN;
    }

    /**
     * Save the ticket and its changes, then post them to the chat and tell the participants to refetch.
     */
    private UUID commit(SupportTicket ticket, UUID actorId, List<Change> changes) {
        SupportTicket saved = supportTicketRepository.save(ticket);
        UUID ticketId = saved.getId();
        UUID conversationId = saved.getConversation().getId();
        String reference = saved.getReference();

        List<SupportTicketEvent> events = changes.stream().map(change -> {
            SupportTicketEvent event = new SupportTicketEvent();
            event.setTicket(saved);
            event.setType(change.type());
            event.setActorId(actorId);
            event.setTargetUserId(change.targetUserId());
            event.setFromValue(change.fromValue());
            event.setToValue(change.toValue());
            return event;
        }).toList();
        supportTicketEventRepository.saveAll(events);
        log.info("Support ticket changed [id={}, actorId={}, changes={}]", ticketId, actorId,
                changes.stream().map(Change::type).toList());
        publishIntegrationEvents(saved, actorId, changes);

        // Posting clears the persistence context, so everything above must be saved first.
        for (Change change : changes) {
            String body = systemMessageBody(change, reference);
            if (body != null) {
                chatMessageService.postSystemMessage(conversationId, actorId,
                        new SystemMessageEvent(change.type(), change.targetUserId(), change.fromValue(),
                                change.toValue()),
                        body);
            }
        }
        chatEventPublisher.publish(conversationService.activeParticipantIds(conversationId),
                ChatEventType.CONVERSATION_UPDATED, conversationId, null);
        // Staff watching the queue see every change, including to tickets they are not on.
        chatEventPublisher.publishToTopic(ChatStompAuthChannelInterceptor.SUPPORT_QUEUE_TOPIC,
                ChatEventType.TICKET_UPDATED, conversationId, toResponse(requireTicket(ticketId), true));
        return ticketId;
    }

    private void publishIntegrationEvents(SupportTicket ticket, UUID actorId, List<Change> changes) {
        UUID conversationId = ticket.getConversation().getId();
        for (Change change : changes) {
            if (change.type() == SupportTicketEventType.CREATED) {
                SupportTicketCreatedEvent created = new SupportTicketCreatedEvent(ticket.getId(),
                        ticket.getReference(), conversationId, ticket.getRequesterId(), ticket.getOpenedByUserId(),
                        ticket.getSubject(), ticket.getCategory().name());
                AfterCommit.run(() -> messageProducer.publish(
                        created, "", SupportTicketCreatedContract.SUPPORT_TICKET_CREATED_QUEUE_NAME));
            } else if (change.type() == SupportTicketEventType.STATUS_CHANGED) {
                SupportTicketStatusChangedEvent statusChanged = new SupportTicketStatusChangedEvent(ticket.getId(),
                        ticket.getReference(), conversationId, ticket.getRequesterId(), actorId,
                        change.fromValue(), change.toValue(), ticket.getSubject());
                AfterCommit.run(() -> messageProducer.publish(statusChanged, "",
                        SupportTicketStatusChangedContract.SUPPORT_TICKET_STATUS_CHANGED_QUEUE_NAME));
            }
        }
    }

    private static String systemMessageBody(Change change, String reference) {
        return switch (change.type()) {
            case CREATED -> "Ticket %s opened".formatted(reference);
            case STATUS_CHANGED -> "Status changed to %s".formatted(humanize(change.toValue()));
            case CATEGORY_CHANGED -> "Category changed to %s".formatted(humanize(change.toValue()));
            case ASSIGNED -> "A support agent took the ticket";
            case UNASSIGNED -> "The ticket is waiting for a support agent";
            case STAFF_JOINED -> "A support agent joined";
            case STAFF_LEFT -> "A support agent left";
            // Priority is for staff only, and the customer knows their own rating.
            case PRIORITY_CHANGED, RATED -> null;
        };
    }

    private static String humanize(String enumName) {
        String spaced = enumName.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static ConversationParticipant newParticipant(Conversation conversation, UUID userId,
                                                          ParticipantRole role, Instant now) {
        ConversationParticipant participant = new ConversationParticipant();
        participant.setConversation(conversation);
        participant.setUserId(userId);
        participant.setRole(role);
        participant.setJoinedAt(now);
        participant.setLastActivityAt(now);
        return participant;
    }

    private SupportTicket requireTicket(UUID id) {
        return supportTicketRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket with id=%s not found".formatted(id)));
    }

    private SupportTicketResponse toResponse(SupportTicket ticket, boolean isStaffView) {
        return supportTicketMapper.toResponse(ticket,
                participantRepository.findByConversationIdIn(List.of(ticket.getConversation().getId())), isStaffView);
    }

    private List<SupportTicketResponse> toResponses(List<SupportTicket> tickets, boolean isStaffView) {
        if (tickets.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ConversationParticipant>> participantsByConversation = participantRepository
                .findByConversationIdIn(tickets.stream().map(ticket -> ticket.getConversation().getId()).toList())
                .stream()
                .collect(Collectors.groupingBy(participant -> participant.getConversation().getId()));
        return tickets.stream()
                .map(ticket -> supportTicketMapper.toResponse(ticket,
                        participantsByConversation.getOrDefault(ticket.getConversation().getId(), List.of()),
                        isStaffView))
                .toList();
    }

    /**
     * One change to a ticket, before it is recorded.
     *
     * @param type the kind of change
     * @param targetUserId the user it concerns, or null
     * @param fromValue the value before, or null
     * @param toValue the value after, or null
     */
    private record Change(SupportTicketEventType type, UUID targetUserId, String fromValue, String toValue) {
    }
}
