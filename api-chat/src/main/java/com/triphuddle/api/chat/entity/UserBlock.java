package com.triphuddle.api.chat.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * One user blocking another from private chats with them. Unblocking soft-deletes the row.
 */
@Entity
@Table(
        name = "user_blocks",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_block", columnNames = {"blocker_id", "blocked_id"})
)
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class UserBlock extends BaseJpaEntity {

    @Column(name = "blocker_id", nullable = false)
    private UUID blockerId;

    @Column(name = "blocked_id", nullable = false)
    private UUID blockedId;
}
