package com.triphuddle.api.chat.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A private chat between two users or the chat of a group, with a summary of its last message.
 */
@Entity
@Table(
        name = "conversations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_conversation_group", columnNames = "group_id"),
                @UniqueConstraint(name = "uk_conversation_private_key", columnNames = "private_key")
        }
)
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Conversation extends BaseJpaEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private ConversationType type;

    @Column(name = "group_id")
    private UUID groupId;

    // The two user IDs, sorted and joined: the unique constraint on it keeps one private chat per pair.
    @Column(name = "private_key", length = 73)
    private String privateKey;

    @Column(name = "name")
    private String name;

    @Column(name = "read_only_at")
    private Instant readOnlyAt;

    @Column(name = "last_message_id", length = 24)
    private String lastMessageId;

    @Column(name = "last_message_preview", length = 200)
    private String lastMessagePreview;

    @Column(name = "last_message_sender_id")
    private UUID lastMessageSenderId;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;
}
