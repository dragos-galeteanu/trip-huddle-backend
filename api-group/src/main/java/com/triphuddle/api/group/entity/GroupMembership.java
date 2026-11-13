package com.triphuddle.api.group.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.UUID;

/**
 * A user's place in a group, with their role there. A user has at most one per group.
 */
@Entity
@Table(name = "group_memberships")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class GroupMembership extends BaseJpaEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, foreignKey = @ForeignKey(name = "fk_group_membership_group"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Group group;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private GroupRole role;
}
