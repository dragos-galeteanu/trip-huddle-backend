package com.triphuddle.api.platform.security;

import com.triphuddle.api.platform.dto.CreateCurrencyRequest;
import com.triphuddle.api.platform.dto.UpdateCurrencyRequest;
import com.triphuddle.api.platform.fixtures.CurrencyFixtures;
import com.triphuddle.testsupport.security.AbstractAuthorizationPolicyTest;
import com.triphuddle.testsupport.security.AuthorizationMatrix;

import java.util.UUID;

class CurrencyAuthorizationPolicyTest
        extends AbstractAuthorizationPolicyTest<CreateCurrencyRequest, UpdateCurrencyRequest, CurrencyAuthorizationPolicy> {

    @Override
    protected CurrencyAuthorizationPolicy policy() {
        return new CurrencyAuthorizationPolicy();
    }

    @Override
    protected AuthorizationMatrix matrix() {
        return PlatformMatrices.REFERENCE_DATA;
    }

    @Override
    protected CreateCurrencyRequest createRequest() {
        return CurrencyFixtures.createRequest().build();
    }

    @Override
    protected UpdateCurrencyRequest updateRequest() {
        return CurrencyFixtures.updateRequest(UUID.randomUUID()).build();
    }
}
