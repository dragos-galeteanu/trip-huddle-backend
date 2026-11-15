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
 * A user's place in a conversation: what they have read, whether it is muted and when they left.
 */
@Entity
@Table(
        name = "conversation_participants",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_conversation_participant", columnNames = {"conversation_id", "user_id"}),
        indexes = @Index(name = "ix_participant_user_activity", columnList = "user_id, last_activity_at")
)
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ConversationParticipant extends BaseJpaEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_participant_conversation"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Conversation conversation;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private ParticipantRole role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    // Set when the user leaves the group; they keep reading the history up to this moment.
    @Column(name = "left_at")
    private Instant leftAt;

    // Advances only while the participant is active, so an ex-member's list never shows later activity.
    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "last_read_message_id", length = 24)
    private String lastReadMessageId;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Column(name = "unread_count", nullable = false)
    private int unreadCount;

    @Column(name = "is_muted", nullable = false)
    private boolean isMuted;

    // Null while muted means muted until unmuted.
    @Column(name = "muted_until")
    private Instant mutedUntil;

    /**
     * Whether the participant can still take part, not only read.
     *
     * @return true if the participant has not left
     */
    public boolean isActive() {
        return leftAt == null;
    }

    /**
     * Whether the conversation is muted for the participant at the given moment.
     *
     * @param now the moment to check
     * @return true if muted indefinitely or until a later moment
     */
    public boolean isMutedAt(Instant now) {
        return isMuted && (mutedUntil == null || mutedUntil.isAfter(now));
    }
}
