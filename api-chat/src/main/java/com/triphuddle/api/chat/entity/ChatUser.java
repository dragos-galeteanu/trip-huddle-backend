package com.triphuddle.api.chat.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A platform user this service has heard of from api-user, so private chats only target real users.
 */
@Entity
@Table(name = "chat_users", uniqueConstraints = @UniqueConstraint(name = "uk_chat_user", columnNames = "user_id"))
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ChatUser extends BaseJpaEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;
}
