package com.triphuddle.api.user.security;

import com.triphuddle.common.security.BaseAuthorizationPolicy;
import com.triphuddle.api.user.dto.RegisterRequest;
import com.triphuddle.api.user.dto.UpdateUserRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Users are readable by any signed-in user; a user is changed by themselves or by Admin or Support.
 */
@Component("UserPolicy")
public class UserAuthorizationPolicy implements BaseAuthorizationPolicy<RegisterRequest, UpdateUserRequest, UUID> {

    @Override
    public boolean canCreate(Authentication authentication, RegisterRequest registerRequest) {
        return false;
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
    public boolean canUpdate(Authentication authentication, UpdateUserRequest updateUserRequest) {
        return isAdminOrSupport(authentication) || isCurrentUser(authentication, updateUserRequest.getId());
    }

    @Override
    public boolean canDelete(Authentication authentication, UUID id) {
        return isCurrentUser(authentication, id);
    }
}
