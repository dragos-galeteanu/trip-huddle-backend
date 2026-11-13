package com.triphuddle.api.group.repository;

import com.triphuddle.api.group.entity.GroupInvitation;
import com.triphuddle.api.group.entity.GroupInvitationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link GroupInvitation}.
 */
@Repository
public interface GroupInvitationRepository extends BaseJpaRepository<GroupInvitation, UUID> {

    /**
     * Find the invitation a token belongs to, leaving out deleted and disabled ones.
     *
     * @param token the token from the invitation email
     * @return the invitation, or empty if no usable one has the token
     */
    Optional<GroupInvitation> findByTokenAndDeletedAtIsNullAndDisabledAtIsNull(UUID token);

    /**
     * Check if a group already has an invitation for an email address in the given status.
     *
     * @param groupId the ID of the group
     * @param email the invited email address, lower-cased
     * @param status the status to look for
     * @return true if such an invitation exists and is not deleted, false otherwise
     */
    boolean existsByGroupIdAndEmailAndStatusAndDeletedAtIsNull(UUID groupId, String email, GroupInvitationStatus status);

    /**
     * Find the invitations of a group that are not deleted.
     *
     * @param groupId the ID of the group
     * @param pageable the page to return
     * @return a page of the group's invitations
     */
    Page<GroupInvitation> findByGroupIdAndDeletedAtIsNull(UUID groupId, Pageable pageable);

    /**
     * Find the usable invitations for an email address in the given status that no user is linked to yet.
     *
     * @param email the invited email address, lower-cased
     * @param status the status to look for
     * @return the matching invitations
     */
    List<GroupInvitation> findByEmailAndStatusAndAcceptedByUserIdIsNullAndDeletedAtIsNullAndDisabledAtIsNull(
            String email, GroupInvitationStatus status);
}
