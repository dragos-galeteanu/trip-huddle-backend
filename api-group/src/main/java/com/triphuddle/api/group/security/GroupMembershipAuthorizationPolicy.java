package com.triphuddle.api.group.security;

import com.triphuddle.api.group.dto.CreateGroupMembershipRequest;
import com.triphuddle.api.group.dto.UpdateGroupMembershipRequest;
import com.triphuddle.api.group.entity.GroupMembership;
import com.triphuddle.api.group.repository.GroupMembershipRepository;
import com.triphuddle.common.security.BaseAuthorizationPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component("GroupMembershipPolicy")
@RequiredArgsConstructor
public class GroupMembershipAuthorizationPolicy
        implements BaseAuthorizationPolicy<CreateGroupMembershipRequest, UpdateGroupMembershipRequest, UUID> {

    private final GroupMembershipRepository groupMembershipRepository;
    private final GroupAccess groupAccess;

    @Override
    public Optional<UUID> resolveOwnerUserId(UUID id) {
        return groupMembershipRepository.findById(id).map(GroupMembership::getUserId);
    }

    @Override
    public boolean canCreate(Authentication authentication, CreateGroupMembershipRequest createGroupMembershipRequest) {
        return isAdminOrSupport(authentication) || groupAccess.isGroupAdmin(authentication, createGroupMembershipRequest.getGroupId());
    }

    @Override
    public boolean canGetById(Authentication authentication, UUID id) {
        return isAdminOrSupportOrInspector(authentication) || groupAccess.isGroupMember(authentication, resolveGroupId(id));
    }

    @Override
    public boolean canGetAll(Authentication authentication) {
        return isAdminOrSupportOrInspector(authentication);
    }

    public boolean canGetAllByGroup(Authentication authentication, UUID groupId) {
        return isAdminOrSupportOrInspector(authentication) || groupAccess.isGroupMember(authentication, groupId);
    }

    @Override
    public boolean canUpdate(Authentication authentication, UpdateGroupMembershipRequest updateGroupMembershipRequest) {
        return isAdminOrSupport(authentication) || groupAccess.isGroupAdmin(authentication, resolveGroupId(updateGroupMembershipRequest.getId()));
    }

    @Override
    public boolean canDelete(Authentication authentication, UUID id) {
        return groupAccess.isGroupAdmin(authentication, resolveGroupId(id)) || isResourceOwner(authentication, id);
    }

    private UUID resolveGroupId(UUID id) {
        if (id == null) {
            return null;
        }
        return groupMembershipRepository.findById(id)
                .map(groupMembership -> groupMembership.getGroup().getId())
                .orElse(null);
    }
}
