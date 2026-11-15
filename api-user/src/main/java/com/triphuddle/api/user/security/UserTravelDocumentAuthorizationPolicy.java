package com.triphuddle.api.user.security;

import com.triphuddle.api.user.dto.TravelDocumentsRequest;
import com.triphuddle.common.security.BaseAuthorizationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Every signed-in user reads and replaces their own travel documents, and only theirs.
 */
@Component("UserTravelDocumentPolicy")
public class UserTravelDocumentAuthorizationPolicy
        implements BaseAuthorizationPolicy<Void, TravelDocumentsRequest, String> {

    @Override
    public boolean canCreate(Authentication authentication, Void request) {
        return false;
    }

    @Override
    public boolean canGetById(Authentication authentication, String id) {
        return false;
    }

    /**
     * Check if the caller may read their own documents.
     *
     * @param authentication the authentication object
     * @return true when signed in
     */
    public boolean canGetMine(Authentication authentication) {
        return isAuthenticated(authentication);
    }

    @Override
    public boolean canGetAll(Authentication authentication) {
        return false;
    }

    @Override
    public boolean canUpdate(Authentication authentication, TravelDocumentsRequest request) {
        // The documents replaced are always the caller's own, so being signed in is enough.
        return isAuthenticated(authentication);
    }

    @Override
    public boolean canDelete(Authentication authentication, String id) {
        return false;
    }

    @Override
    public boolean canDisable(Authentication authentication) {
        return false;
    }
}
