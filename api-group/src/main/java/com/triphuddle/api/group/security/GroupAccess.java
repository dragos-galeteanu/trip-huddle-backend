package com.triphuddle.api.group.security;

import com.triphuddle.api.group.entity.GroupRole;
import com.triphuddle.api.group.repository.GroupMembershipRepository;
import com.triphuddle.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Answers what the caller is within a group, from their active membership; shared by the group policies.
 */
@Component
@RequiredArgsConstructor
public class GroupAccess {

    private final GroupMembershipRepository groupMembershipRepository;

    /**
     * Check if the caller is an active member of the group, whatever their role.
     * @param authentication the authentication object
     * @param groupId the ID of the group
     * @return true if the caller is an active member of the group, false otherwise
     */
    public boolean isGroupMember(Authentication authentication, UUID groupId) {
        if (groupId == null) {
            return false;
        }
        return currentUserId(authentication)
                .map(userId -> groupMembershipRepository
                        .existsByGroupIdAndUserIdAndDeletedAtIsNullAndDisabledAtIsNull(groupId, userId))
                .orElse(false);
    }

    /**
     * Check if the caller is an active Admin of the group.
     * @param authentication the authentication object
     * @param groupId the ID of the group
     * @return true if the caller is an active Admin of the group, false otherwise
     */
    public boolean isGroupAdmin(Authentication authentication, UUID groupId) {
        if (groupId == null) {
            return false;
        }
        return currentUserId(authentication)
                .map(userId -> groupMembershipRepository
                        .existsByGroupIdAndUserIdAndRoleAndDeletedAtIsNullAndDisabledAtIsNull(groupId, userId, GroupRole.ADMIN))
                .orElse(false);
    }

    private Optional<UUID> currentUserId(Authentication authentication) {
        try {
            return Optional.of(SecurityUtils.extractUserId(authentication));
        } catch (AccessDeniedException e) {
            // A token without a platform userId belongs to nobody's membership.
            return Optional.empty();
        }
    }
}
