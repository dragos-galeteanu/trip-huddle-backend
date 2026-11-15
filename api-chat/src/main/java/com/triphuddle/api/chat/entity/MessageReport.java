package com.triphuddle.api.chat.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's report of a message, reviewed by staff.
 */
@Entity
// One OPEN report per message and reporter: a partial index, which JPA can't declare.
@Table(name = "message_reports", indexes = @Index(name = "ix_message_report_status", columnList = "status"))
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class MessageReport extends BaseJpaEntity {

    @Column(name = "message_id", nullable = false, length = 24)
    private String messageId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReportStatus status;

    @Column(name = "resolved_by_user_id")
    private UUID resolvedByUserId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;
}
