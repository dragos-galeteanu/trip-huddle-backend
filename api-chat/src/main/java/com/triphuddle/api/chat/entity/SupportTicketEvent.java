package com.triphuddle.api.chat.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.UUID;

/**
 * One change in a support ticket's history: who made it and what it changed.
 */
@Entity
@Table(name = "support_ticket_events", indexes = @Index(name = "ix_support_ticket_event_ticket", columnList = "ticket_id"))
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SupportTicketEvent extends BaseJpaEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, foreignKey = @ForeignKey(name = "fk_support_ticket_event_ticket"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private SupportTicket ticket;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private SupportTicketEventType type;

    // Null when the system made the change on its own.
    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "from_value", length = 32)
    private String fromValue;

    @Column(name = "to_value", length = 32)
    private String toValue;
}
