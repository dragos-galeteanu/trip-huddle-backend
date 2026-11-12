package com.triphuddle.api.user.security;

import com.triphuddle.common.security.BaseAuthorizationPolicy;
import com.triphuddle.api.user.dto.CreateUserPreferenceRequest;
import com.triphuddle.api.user.dto.UpdateUserPreferenceRequest;
import com.triphuddle.api.user.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component("UserPreferencePolicy")
@RequiredArgsConstructor
public class UserPreferenceAuthorizationPolicy
        implements BaseAuthorizationPolicy<CreateUserPreferenceRequest, UpdateUserPreferenceRequest, String> {

    private final UserPreferenceRepository userPreferenceRepository;

    @Override
    public Optional<UUID> resolveOwnerUserId(String id) {
        if (id == null || !ObjectId.isValid(id)) {
            return Optional.empty();
        }

        return userPreferenceRepository.findOwnerUserIdById(new ObjectId(id));
    }

    @Override
    public boolean canCreate(Authentication authentication, CreateUserPreferenceRequest createUserPreferenceRequest) {
        return isAdminOrSupport(authentication) || isCurrentUser(authentication, createUserPreferenceRequest.getUserId());
    }

    @Override
    public boolean canGetById(Authentication authentication, String id) {
        return isAdminOrSupportOrInspector(authentication) || isResourceOwner(authentication, id);
    }

    @Override
    public boolean canGetAll(Authentication authentication) {
        return isAdminOrSupportOrInspector(authentication);
    }

    @Override
    public boolean canUpdate(Authentication authentication, UpdateUserPreferenceRequest updateUserPreferenceRequest) {
        return isAdminOrSupport(authentication) || isResourceOwner(authentication, updateUserPreferenceRequest.getId());
    }

    @Override
    public boolean canDelete(Authentication authentication, String id) {
        return isAdmin(authentication) || isResourceOwner(authentication, id);
    }
}
