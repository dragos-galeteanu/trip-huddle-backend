package com.triphuddle.user.security;

import com.triphuddle.common.security.BaseAuthorizationPolicy;
import com.triphuddle.user.dto.CreateUserProfileRequest;
import com.triphuddle.user.dto.UpdateUserProfileRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("UserProfilePolicy")
public class UserProfileAuthorizationPolicy implements BaseAuthorizationPolicy<CreateUserProfileRequest, UpdateUserProfileRequest, UUID> {

    @Override
    public boolean canCreate(Authentication authentication, CreateUserProfileRequest createUserProfileRequest) {
        return isAdminOrSupport(authentication) || isCurrentUser(authentication, createUserProfileRequest.getUserId());
    }

    @Override
    public boolean canGetById(Authentication authentication, UUID id) {
        return true;
    }

    @Override
    public boolean canGetAll(Authentication authentication) {
        return true;
    }

    @Override
    public boolean canUpdate(Authentication authentication, UpdateUserProfileRequest updateUserProfileRequest) {
        return isAdminOrSupport(authentication) || isCurrentUser(authentication, updateUserProfileRequest.getUserId());
    }

    @Override
    public boolean canDelete(Authentication authentication, UUID id) {
        return isCurrentUser(authentication, id);
    }
}
