package com.triphuddle.api.payment.repository;

import com.triphuddle.api.payment.entity.GroupAdmin;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link GroupAdmin}.
 */
@Repository
public interface GroupAdminRepository extends BaseJpaRepository<GroupAdmin, UUID> {

    /**
     * Find the copy of a membership.
     *
     * @param groupMembershipId the ID of the membership in api-group
     * @return the copy, or empty if the membership is not an ADMIN one
     */
    Optional<GroupAdmin> findByGroupMembershipId(UUID groupMembershipId);

    /**
     * Check if a user is an admin of a group.
     *
     * @param groupId the ID of the group
     * @param userId the ID of the user
     * @return true if the user has an ADMIN membership of the group
     */
    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);

    /**
     * Forget the copy of a membership.
     *
     * @param groupMembershipId the ID of the membership in api-group
     * @return how many copies were removed, 0 or 1
     */
    @Modifying
    @Query("delete from GroupAdmin a where a.groupMembershipId = :groupMembershipId")
    int deleteByGroupMembershipId(@Param("groupMembershipId") UUID groupMembershipId);

    /**
     * Forget every admin of a group.
     *
     * @param groupId the ID of the group
     * @return how many copies were removed
     */
    @Modifying
    @Query("delete from GroupAdmin a where a.groupId = :groupId")
    int deleteByGroupId(@Param("groupId") UUID groupId);
}
