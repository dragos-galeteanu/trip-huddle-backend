package com.triphuddle.api.platform.security;

import com.triphuddle.common.security.BaseAuthorizationPolicy;
import com.triphuddle.api.platform.dto.CreateCurrencyRequest;
import com.triphuddle.api.platform.dto.UpdateCurrencyRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Reference data: readable by anyone, authenticated or not, writable by admins only.
 */
@Component("CurrencyPolicy")
public class CurrencyAuthorizationPolicy implements BaseAuthorizationPolicy<CreateCurrencyRequest, UpdateCurrencyRequest, UUID> {

    @Override
    public boolean canCreate(Authentication authentication, CreateCurrencyRequest createCurrencyRequest) {
        return isAdminOrSupport(authentication);
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
    public boolean canUpdate(Authentication authentication, UpdateCurrencyRequest updateCurrencyRequest) {
        return isAdminOrSupport(authentication);
    }

    @Override
    public boolean canDisable(Authentication authentication) {
        return isAdminOrSupport(authentication);
    }

    @Override
    public boolean canDelete(Authentication authentication, UUID id) {
        return isAdminOrSupport(authentication);
    }
}
