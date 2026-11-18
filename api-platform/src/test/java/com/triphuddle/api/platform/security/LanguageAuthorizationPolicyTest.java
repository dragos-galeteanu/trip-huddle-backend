package com.triphuddle.api.platform.security;

import com.triphuddle.api.platform.dto.CreateLanguageRequest;
import com.triphuddle.api.platform.dto.UpdateLanguageRequest;
import com.triphuddle.api.platform.fixtures.LanguageFixtures;
import com.triphuddle.testsupport.security.AbstractAuthorizationPolicyTest;
import com.triphuddle.testsupport.security.AuthorizationMatrix;

import java.util.UUID;

class LanguageAuthorizationPolicyTest
        extends AbstractAuthorizationPolicyTest<CreateLanguageRequest, UpdateLanguageRequest, LanguageAuthorizationPolicy> {

    @Override
    protected LanguageAuthorizationPolicy policy() {
        return new LanguageAuthorizationPolicy();
    }

    @Override
    protected AuthorizationMatrix matrix() {
        return PlatformMatrices.REFERENCE_DATA;
    }

    @Override
    protected CreateLanguageRequest createRequest() {
        return LanguageFixtures.createRequest().build();
    }

    @Override
    protected UpdateLanguageRequest updateRequest() {
        return LanguageFixtures.updateRequest(UUID.randomUUID()).build();
    }
}
