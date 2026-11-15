package com.triphuddle.api.destination.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A local copy of a membership in api-group, kept from its events; a group's license applies to its members.
 */
@Entity
@Table(name = "destination_group_members")
@Getter
@Setter
@NoArgsConstructor
public class GroupMember extends BaseJpaEntity {

    @Column(name = "group_membership_id", nullable = false, unique = true)
    private UUID groupMembershipId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // ADMIN or MEMBER, as in api-group.
    @Column(name = "role", nullable = false, length = 16)
    private String role;
}
