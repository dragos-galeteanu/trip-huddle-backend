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

    /**
     * Check if a user is an active member of a group.
     *
     * @param groupId the ID of the group
     * @param userId the ID of the user
     * @return true if the user has an active membership in the group, false otherwise
     */
    boolean existsByGroupIdAndUserIdAndDeletedAtIsNullAndDisabledAtIsNull(UUID groupId, UUID userId);

    /**
     * Check if a user is an active member of a group with the given role.
     *
     * @param groupId the ID of the group
     * @param userId the ID of the user
     * @param role the role to look for
     * @return true if the user has an active membership with the role, false otherwise
     */
    boolean existsByGroupIdAndUserIdAndRoleAndDeletedAtIsNullAndDisabledAtIsNull(UUID groupId, UUID userId, GroupRole role);

    /**
     * Check if a user holds a place in a group, active or disabled.
     *
     * @param groupId the ID of the group
     * @param userId the ID of the user
     * @return true if the user has a membership that is not deleted, false otherwise
     */
    boolean existsByGroupIdAndUserIdAndDeletedAtIsNull(UUID groupId, UUID userId);

    /**
     * Count the active members of a group with the given role.
     *
     * @param groupId the ID of the group
     * @param role the role to count
     * @return the number of active memberships with the role
     */
    long countByGroupIdAndRoleAndDeletedAtIsNullAndDisabledAtIsNull(UUID groupId, GroupRole role);

    /**
     * Find the memberships of a group that are not deleted.
     *
     * @param groupId the ID of the group
     * @param pageable the page to return
     * @return a page of the group's memberships
     */
    Page<GroupMembership> findByGroupIdAndDeletedAtIsNull(UUID groupId, Pageable pageable);

    /**
     * Find the active memberships of a user.
     *
     * @param userId the ID of the user
     * @param pageable the page to return
     * @return a page of the user's active memberships
     */
    Page<GroupMembership> findByUserIdAndDeletedAtIsNullAndDisabledAtIsNull(UUID userId, Pageable pageable);
}
