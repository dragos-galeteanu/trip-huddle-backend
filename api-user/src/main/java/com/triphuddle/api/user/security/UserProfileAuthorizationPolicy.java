package com.triphuddle.api.user.security;

import com.triphuddle.common.security.BaseAuthorizationPolicy;
import com.triphuddle.api.user.dto.CreateUserProfileRequest;
import com.triphuddle.api.user.dto.UpdateUserProfileRequest;
import com.triphuddle.api.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Profiles are readable by any signed-in user; a profile is changed by its owner or by Admin or Support.
 */
@Component("UserProfilePolicy")
@RequiredArgsConstructor
public class UserProfileAuthorizationPolicy implements BaseAuthorizationPolicy<CreateUserProfileRequest, UpdateUserProfileRequest, UUID> {

    private final UserProfileRepository userProfileRepository;

    @Override
    public Optional<UUID> resolveOwnerUserId(UUID id) {
        return userProfileRepository.findOwnerUserIdById(id);
    }

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
        return isAdminOrSupport(authentication) || isResourceOwner(authentication, updateUserProfileRequest.getId());
    }

    @Override
    public boolean canDelete(Authentication authentication, UUID id) {
        return isResourceOwner(authentication, id);
    }
}
