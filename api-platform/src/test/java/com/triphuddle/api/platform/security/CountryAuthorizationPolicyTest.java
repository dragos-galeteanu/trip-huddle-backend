package com.triphuddle.api.platform.security;

import com.triphuddle.api.platform.dto.CreateCountryRequest;
import com.triphuddle.api.platform.dto.UpdateCountryRequest;
import com.triphuddle.api.platform.fixtures.CountryFixtures;
import com.triphuddle.testsupport.security.AbstractAuthorizationPolicyTest;
import com.triphuddle.testsupport.security.AuthorizationMatrix;

import java.util.UUID;

class CountryAuthorizationPolicyTest
        extends AbstractAuthorizationPolicyTest<CreateCountryRequest, UpdateCountryRequest, CountryAuthorizationPolicy> {

    @Override
    protected CountryAuthorizationPolicy policy() {
        return new CountryAuthorizationPolicy();
    }

    @Override
    protected AuthorizationMatrix matrix() {
        return PlatformMatrices.REFERENCE_DATA;
    }

    @Override
    protected CreateCountryRequest createRequest() {
        return CountryFixtures.createRequest().build();
    }

    @Override
    protected UpdateCountryRequest updateRequest() {
        return CountryFixtures.updateRequest(UUID.randomUUID()).build();
    }
}
