package com.triphuddle.api.group.security;

import com.triphuddle.api.group.dto.CreateGroupRequest;
import com.triphuddle.api.group.dto.UpdateGroupRequest;
import com.triphuddle.common.security.BaseAuthorizationPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("GroupPolicy")
@RequiredArgsConstructor
public class GroupAuthorizationPolicy implements BaseAuthorizationPolicy<CreateGroupRequest, UpdateGroupRequest, UUID> {

    private final GroupAccess groupAccess;

    @Override
    public boolean canCreate(Authentication authentication, CreateGroupRequest createGroupRequest) {
        return isAuthenticated(authentication);
    }

    @Override
    public boolean canGetById(Authentication authentication, UUID id) {
        return isAdminOrSupportOrInspector(authentication) || groupAccess.isGroupMember(authentication, id);
    }

    @Override
    public boolean canGetAll(Authentication authentication) {
        return isAdminOrSupportOrInspector(authentication);
    }

    public boolean canGetMine(Authentication authentication) {
        return isAuthenticated(authentication);
    }

    @Override
    public boolean canUpdate(Authentication authentication, UpdateGroupRequest updateGroupRequest) {
        return isAdminOrSupport(authentication) || groupAccess.isGroupAdmin(authentication, updateGroupRequest.getId());
    }

    @Override
    public boolean canDelete(Authentication authentication, UUID id) {
        return groupAccess.isGroupAdmin(authentication, id);
    }
}
