package com.triphuddle.api.license.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A local copy of an ADMIN membership in api-group, kept from its events; group admins may subscribe their group.
 */
@Entity
@Table(name = "license_group_admins")
@Getter
@Setter
@NoArgsConstructor
public class GroupAdmin extends BaseJpaEntity {

    @Column(name = "group_membership_id", nullable = false, unique = true)
    private UUID groupMembershipId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;
}
