package com.triphuddle.api.destination.repository;

import com.triphuddle.api.destination.entity.GroupMember;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link GroupMember}.
 */
@Repository
public interface GroupMemberRepository extends BaseJpaRepository<GroupMember, UUID> {

    /**
     * Find the copy of a membership.
     *
     * @param groupMembershipId the ID of the membership in api-group
     * @return the copy
     */
    Optional<GroupMember> findByGroupMembershipId(UUID groupMembershipId);

    /**
     * Check if a user is a member of a group.
     *
     * @param groupId the ID of the group
     * @param userId the ID of the user
     * @return true if a membership of theirs is recorded
     */
    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);

    /**
     * Check if a user has a role in a group.
     *
     * @param groupId the ID of the group
     * @param userId the ID of the user
     * @param role ADMIN or MEMBER
     * @return true if a membership of theirs has it
     */
    boolean existsByGroupIdAndUserIdAndRole(UUID groupId, UUID userId, String role);

    /**
     * Get the users of a group's memberships.
     *
     * @param groupId the ID of the group
     * @return their IDs, without repeats
     */
    @Query("select distinct m.userId from GroupMember m where m.groupId = :groupId")
    List<UUID> findUserIdsByGroupId(@Param("groupId") UUID groupId);

    /**
     * Forget a membership.
     *
     * @param groupMembershipId the ID of the membership in api-group
     * @return how many copies were removed, 0 or 1
     */
    @Modifying
    @Query("delete from GroupMember m where m.groupMembershipId = :groupMembershipId")
    int deleteByGroupMembershipId(@Param("groupMembershipId") UUID groupMembershipId);

    /**
     * Forget every member of a group.
     *
     * @param groupId the ID of the group
     * @return how many copies were removed
     */
    @Modifying
    @Query("delete from GroupMember m where m.groupId = :groupId")
    int deleteByGroupId(@Param("groupId") UUID groupId);
}
