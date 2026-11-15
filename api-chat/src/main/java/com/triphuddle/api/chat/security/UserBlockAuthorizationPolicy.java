package com.triphuddle.api.chat.security;

import com.triphuddle.api.chat.dto.BlockUserRequest;
import com.triphuddle.common.security.BaseAuthorizationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Every signed-in user manages their own blocks, and only theirs.
 */
@Component("UserBlockPolicy")
public class UserBlockAuthorizationPolicy implements BaseAuthorizationPolicy<BlockUserRequest, Void, UUID> {

    @Override
    public boolean canCreate(Authentication authentication, BlockUserRequest request) {
        return isAuthenticated(authentication);
    }

    @Override
    public boolean canGetById(Authentication authentication, UUID id) {
        return false;
    }

    @Override
    public boolean canGetAll(Authentication authentication) {
        // The list only ever holds the caller's own blocks.
        return isAuthenticated(authentication);
    }

    @Override
    public boolean canUpdate(Authentication authentication, Void request) {
        return false;
    }

    @Override
    public boolean canDelete(Authentication authentication, UUID blockedUserId) {
        // The route names the blocked user; the service only ever lifts the caller's own block.
        return isAuthenticated(authentication);
    }

    @Override
    public boolean canDisable(Authentication authentication) {
        return false;
    }
}
