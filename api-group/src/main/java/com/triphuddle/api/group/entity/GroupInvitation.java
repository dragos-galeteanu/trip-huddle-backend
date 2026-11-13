package com.triphuddle.api.group.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * An invitation for an email address to join a group, answered through its secret token.
 */
@Entity
@Table(name = "group_invitations")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class GroupInvitation extends BaseJpaEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, foreignKey = @ForeignKey(name = "fk_group_invitation_group"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Group group;

    @Column(name = "email", nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private GroupRole role;

    @Column(name = "token", nullable = false, unique = true)
    @ToString.Exclude
    private UUID token;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private GroupInvitationStatus status = GroupInvitationStatus.PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "invited_by_user_id", nullable = false)
    private UUID invitedByUserId;

    @Column(name = "accepted_by_user_id")
    private UUID acceptedByUserId;
}
