package com.triphuddle.api.chat.service;

import com.triphuddle.common.filter.CursorPaginationFilter;
import com.triphuddle.common.dto.PageCursor;
import com.triphuddle.common.dto.CursorPaginated;
import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.chat.ChatMessageReportedContract;
import com.triphuddle.amqp.chat.ChatMessageReportedEvent;
import com.triphuddle.api.chat.dto.CreateMessageReportRequest;
import com.triphuddle.api.chat.dto.MessageReportResponse;
import com.triphuddle.api.chat.dto.MessageResponse;
import com.triphuddle.api.chat.dto.ResolveMessageReportRequest;
import com.triphuddle.api.chat.entity.ChatMessage;
import com.triphuddle.api.chat.entity.ConversationType;
import com.triphuddle.api.chat.entity.MessageKind;
import com.triphuddle.api.chat.entity.MessageReport;
import com.triphuddle.api.chat.entity.ReportStatus;
import com.triphuddle.api.chat.mapper.ChatMessageMapper;
import com.triphuddle.api.chat.repository.ChatMessageRepository;
import com.triphuddle.api.chat.repository.MessageReportRepository;
import com.triphuddle.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Takes users' reports of messages and lets staff review them, see their context and act on them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MessageReportService {

    private static final int CONTEXT_AROUND = 10;

    private final MessageReportRepository messageReportRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageService chatMessageService;
    private final ConversationService conversationService;
    private final ChatMessageMapper chatMessageMapper;
    private final RabbitMQMessageProducer messageProducer;

    /**
     * Report a message; a user has at most one open report per message.
     *
     * @param messageId the ID of the message
     * @param reporterId the ID of the caller
     * @param request why the message is reported
     * @return the report, without the message
     */
    public MessageReportResponse create(String messageId, UUID reporterId, CreateMessageReportRequest request) {
        ChatMessage message = chatMessageService.getEntityById(messageId);
        // Staff already take part in a support chat, so there is nobody to report to.
        if (conversationService.requireConversation(message.getConversationId()).getType() == ConversationType.SUPPORT) {
            throw new IllegalStateException("Messages in support chats cannot be reported");
        }
        if (message.getKind() == MessageKind.SYSTEM) {
            throw new IllegalStateException("System messages cannot be reported");
        }
        if (reporterId.equals(message.getSenderId())) {
            throw new IllegalStateException("You cannot report your own message");
        }
        if (messageReportRepository.existsByMessageIdAndReporterIdAndStatus(messageId, reporterId, ReportStatus.OPEN)) {
            throw new IllegalStateException("You already reported this message");
        }

        MessageReport report = new MessageReport();
        report.setMessageId(message.getId().toHexString());
        report.setConversationId(message.getConversationId());
        report.setReporterId(reporterId);
        report.setReason(request.getReason().strip());
        report.setStatus(ReportStatus.OPEN);

        MessageReport saved = messageReportRepository.save(report);
        log.info("Chat message reported [reportId={}, messageId={}, reporterId={}]",
                saved.getId(), messageId, reporterId);

        AfterCommit.run(() -> messageProducer.publish(
                new ChatMessageReportedEvent(saved.getId(), saved.getMessageId(), saved.getConversationId(), reporterId),
                "",
                ChatMessageReportedContract.CHAT_MESSAGE_REPORTED_QUEUE_NAME
        ));
        // The reporter does not get the message back: they already see it, and a deleted one stays hidden.
        return toResponse(saved, null);
    }

    /**
     * Get the reports with a status, oldest first, for staff.
     *
     * @param status the status of the reports
     * @param filter the cursor and page size
     * @return a page of reports, each with the reported message
     */
    @Transactional(readOnly = true)
    public CursorPaginated<MessageReportResponse> getAll(ReportStatus status, CursorPaginationFilter filter) {
        PageCursor cursor = PageCursor.decode(filter.getCursor(), 2);
        boolean isFirst = cursor == null;
        List<MessageReport> found = messageReportRepository.findByStatus(status, isFirst,
                isFirst ? PageCursor.FIRST_INSTANT : cursor.instantAt(0),
                isFirst ? PageCursor.FIRST_ID : cursor.uuidAt(1), filter.toLimit());
        return CursorPaginated.of(found, filter.getLimit(), report -> PageCursor.of(report.getCreatedAt(),
                report.getId()), reports -> reports.stream().map(this::toStaffResponse).toList());
    }

    /**
     * Get a report with the reported message, for staff.
     *
     * @param id the ID of the report
     * @return the report
     */
    @Transactional(readOnly = true)
    public MessageReportResponse getById(UUID id) {
        return toStaffResponse(getEntityById(id));
    }

    /**
     * Get the messages around the reported one, for staff to judge it in context.
     *
     * @param id the ID of the report
     * @return the messages before, the reported one and those after, oldest first
     */
    @Transactional(readOnly = true)
    public List<MessageResponse> getContext(UUID id) {
        MessageReport report = getEntityById(id);
        return chatMessageService.getModerationContext(chatMessageService.getEntityById(report.getMessageId()),
                CONTEXT_AROUND);
    }

    /**
     * Resolve an open report, deleting the message too if asked.
     *
     * @param id the ID of the report
     * @param staffUserId the ID of the staff member
     * @param request the outcome
     * @return the resolved report
     */
    public MessageReportResponse resolve(UUID id, UUID staffUserId, ResolveMessageReportRequest request) {
        if (request.getStatus() == ReportStatus.OPEN) {
            throw new IllegalStateException("A report is resolved as DISMISSED or ACTION_TAKEN");
        }
        MessageReport report = getEntityById(id);
        if (report.getStatus() != ReportStatus.OPEN) {
            throw new IllegalStateException("Report with id=%s is already resolved".formatted(id));
        }

        if (Boolean.TRUE.equals(request.getIsMessageRemoved())) {
            chatMessageService.deleteAsModerator(report.getMessageId(), staffUserId);
        }

        report.setStatus(request.getStatus());
        report.setResolutionNote(request.getResolutionNote());
        report.setResolvedByUserId(staffUserId);
        report.setResolvedAt(Instant.now());
        MessageReport saved = messageReportRepository.save(report);
        log.info("Message report resolved [id={}, status={}, staffUserId={}, isMessageRemoved={}]",
                saved.getId(), saved.getStatus(), staffUserId, request.getIsMessageRemoved());
        return toStaffResponse(saved);
    }

    private MessageReport getEntityById(UUID id) {
        return messageReportRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Message report with id=%s not found".formatted(id)));
    }

    private MessageReportResponse toStaffResponse(MessageReport report) {
        MessageResponse message = chatMessageRepository.findById(new ObjectId(report.getMessageId()))
                .map(chatMessageMapper::toModerationResponse)
                .orElse(null);
        return toResponse(report, message);
    }

    private static MessageReportResponse toResponse(MessageReport report, MessageResponse message) {
        return new MessageReportResponse(
                report.getId(),
                report.getMessageId(),
                report.getConversationId(),
                report.getReporterId(),
                report.getReason(),
                report.getStatus(),
                report.getResolvedByUserId(),
                report.getResolvedAt(),
                report.getResolutionNote(),
                message,
                report.getCreatedAt()
        );
    }
}
