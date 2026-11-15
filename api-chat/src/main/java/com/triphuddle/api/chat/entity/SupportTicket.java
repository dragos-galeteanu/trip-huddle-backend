package com.triphuddle.api.chat.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * A support ticket: the status, assignee and subject of a {@code SUPPORT} conversation.
 */
@Entity
@Table(
        name = "support_tickets",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_support_ticket_conversation", columnNames = "conversation_id"),
                @UniqueConstraint(name = "uk_support_ticket_number", columnNames = "number")
        },
        indexes = {
                @Index(name = "ix_support_ticket_status", columnList = "status"),
                @Index(name = "ix_support_ticket_requester", columnList = "requester_id, status"),
                @Index(name = "ix_support_ticket_assignee", columnList = "assignee_id, status")
        }
)
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SupportTicket extends BaseJpaEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_support_ticket_conversation"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Conversation conversation;

    // Drawn from support_ticket_number_seq; shown to people as TH-<number>.
    @Column(name = "number", nullable = false)
    private long number;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    // The requester for a ticket they opened, or the staff member who opened it towards them.
    @Column(name = "opened_by_user_id", nullable = false)
    private UUID openedByUserId;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 16)
    private TicketCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TicketStatus status;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "linked_conversation_id")
    private UUID linkedConversationId;

    @Column(name = "message_id", length = 24)
    private String messageId;

    @Column(name = "message_report_id")
    private UUID messageReportId;

    // The first reply a staff member wrote to the customer, for response-time reporting.
    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    // The last time the ticket was resolved; kept when it is reopened.
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by_user_id")
    private UUID closedByUserId;

    // The customer's 1-5 rating of the support, given once the ticket is resolved or closed.
    @Column(name = "rating")
    private Integer rating;

    @Column(name = "rating_comment", length = 1000)
    private String ratingComment;

    @Column(name = "rated_at")
    private Instant ratedAt;

    /**
     * The number people quote for the ticket.
     *
     * @return the number prefixed with {@code TH-}
     */
    public String getReference() {
        return "TH-" + number;
    }
}
