package com.triphuddle.api.group.security;

import com.triphuddle.api.group.dto.CreateGroupInvitationRequest;
import com.triphuddle.api.group.dto.UpdateGroupInvitationRequest;
import com.triphuddle.api.group.repository.GroupInvitationRepository;
import com.triphuddle.common.security.BaseAuthorizationPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Invitations are managed by the group's Admins; accepting and rejecting are public and guarded by the token.
 */
@Component("GroupInvitationPolicy")
@RequiredArgsConstructor
public class GroupInvitationAuthorizationPolicy
        implements BaseAuthorizationPolicy<CreateGroupInvitationRequest, UpdateGroupInvitationRequest, UUID> {

    private final GroupInvitationRepository groupInvitationRepository;
    private final GroupAccess groupAccess;

    @Override
    public boolean canCreate(Authentication authentication, CreateGroupInvitationRequest createGroupInvitationRequest) {
        return isAdminOrSupport(authentication) || groupAccess.isGroupAdmin(authentication, createGroupInvitationRequest.getGroupId());
    }

    @Override
    public boolean canGetById(Authentication authentication, UUID id) {
        return isAdminOrSupportOrInspector(authentication) || groupAccess.isGroupMember(authentication, resolveGroupId(id));
    }

    @Override
    public boolean canGetAll(Authentication authentication) {
        return isAdminOrSupportOrInspector(authentication);
    }

    /**
     * Check if the user can list the invitations of a group.
     *
     * @param authentication the authentication object
     * @param groupId the ID of the group
     * @return true if the user is staff or an Admin of the group, false otherwise
     */
    public boolean canGetAllByGroup(Authentication authentication, UUID groupId) {
        return isAdminOrSupportOrInspector(authentication) || groupAccess.isGroupAdmin(authentication, groupId);
    }

    @Override
    public boolean canUpdate(Authentication authentication, UpdateGroupInvitationRequest updateGroupInvitationRequest) {
        return isAdminOrSupport(authentication)
                || groupAccess.isGroupAdmin(authentication, resolveGroupId(updateGroupInvitationRequest.getId()));
    }

    /**
     * Check if the user can cancel an invitation.
     *
     * @param authentication the authentication object
     * @param id the ID of the invitation
     * @return true if the user is Admin, Support or an Admin of the invitation's group, false otherwise
     */
    public boolean canCancel(Authentication authentication, UUID id) {
        return isAdminOrSupport(authentication) || groupAccess.isGroupAdmin(authentication, resolveGroupId(id));
    }

    @Override
    public boolean canDelete(Authentication authentication, UUID id) {
        return groupAccess.isGroupAdmin(authentication, resolveGroupId(id));
    }

    private UUID resolveGroupId(UUID id) {
        if (id == null) {
            return null;
        }
        return groupInvitationRepository.findById(id)
                .map(groupInvitation -> groupInvitation.getGroup().getId())
                .orElse(null);
    }
}
