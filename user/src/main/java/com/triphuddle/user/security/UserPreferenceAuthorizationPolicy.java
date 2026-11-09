package com.triphuddle.user.security;

import com.triphuddle.common.security.BaseAuthorizationPolicy;
import com.triphuddle.user.dto.CreateUserPreferenceRequest;
import com.triphuddle.user.dto.UpdateUserPreferenceRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("UserPreferencePolicy")
public class UserPreferenceAuthorizationPolicy
        implements BaseAuthorizationPolicy<CreateUserPreferenceRequest, UpdateUserPreferenceRequest, UUID> {

    @Override
    public boolean canCreate(Authentication authentication, CreateUserPreferenceRequest createUserPreferenceRequest) {
        return isAdminOrSupport(authentication) || isCurrentUser(authentication, createUserPreferenceRequest.getUserId());
    }

    @Override
    public boolean canGetById(Authentication authentication, UUID id) {
        return isAdminOrSupportOrInspector(authentication) || isCurrentUser(authentication, id);
    }

    @Override
    public boolean canGetAll(Authentication authentication) {
        return isAdminOrSupportOrInspector(authentication);
    }

    @Override
    public boolean canUpdate(Authentication authentication, UpdateUserPreferenceRequest updateUserPreferenceRequest) {
        return isAdminOrSupport(authentication) || isCurrentUser(authentication, updateUserPreferenceRequest.getUserId());
    }

    @Override
    public boolean canDelete(Authentication authentication, UUID id) {
        return isCurrentUser(authentication, id);
    }
}
