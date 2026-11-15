package com.triphuddle.api.payment.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A local copy of an ADMIN membership in api-group, kept from its events; group admins pay for their group.
 */
@Entity
@Table(name = "payment_group_admins")
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
