package com.triphuddle.api.group.repository;

import com.triphuddle.api.group.entity.GroupMembership;
import com.triphuddle.api.group.entity.GroupRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Methods ending in {@code DeletedAtIsNullAndDisabledAtIsNull} see active memberships only.
 */
@Repository
public interface GroupMembershipRepository extends BaseJpaRepository<GroupMembership, UUID> {

    boolean existsByGroupIdAndUserIdAndDeletedAtIsNullAndDisabledAtIsNull(UUID groupId, UUID userId);

    boolean existsByGroupIdAndUserIdAndRoleAndDeletedAtIsNullAndDisabledAtIsNull(UUID groupId, UUID userId, GroupRole role);

    // Not filtered on disabledAt: a disabled membership still occupies the user's place in the group.
    boolean existsByGroupIdAndUserIdAndDeletedAtIsNull(UUID groupId, UUID userId);

    long countByGroupIdAndRoleAndDeletedAtIsNullAndDisabledAtIsNull(UUID groupId, GroupRole role);

    Page<GroupMembership> findByGroupIdAndDeletedAtIsNull(UUID groupId, Pageable pageable);

    Page<GroupMembership> findByUserIdAndDeletedAtIsNullAndDisabledAtIsNull(UUID userId, Pageable pageable);
}
