package com.triphuddle.api.user.security;

import com.triphuddle.api.user.dto.CreateUserProfileRequest;
import com.triphuddle.api.user.dto.UpdateUserProfileRequest;
import com.triphuddle.api.user.entity.UserProfile;
import com.triphuddle.api.user.fixtures.UserFixtures;
import com.triphuddle.api.user.repository.UserProfileRepository;
import com.triphuddle.testsupport.security.AbstractAuthorizationPolicyTest;
import com.triphuddle.testsupport.security.AuthorizationMatrix;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class UserProfileAuthorizationPolicyTest extends AbstractAuthorizationPolicyTest<CreateUserProfileRequest,
        UpdateUserProfileRequest, UserProfileAuthorizationPolicy> {

    private final UserProfileRepository profiles = mock(UserProfileRepository.class);

    @Override
    protected UserProfileAuthorizationPolicy policy() {
        return new UserProfileAuthorizationPolicy(profiles);
    }

    @Override
    protected AuthorizationMatrix matrix() {
        return UserMatrices.PROFILES;
    }

    @Override
    protected CreateUserProfileRequest createRequest() {
        return UserFixtures.createProfile(UUID.randomUUID()).build();
    }

    @Override
    protected UpdateUserProfileRequest updateRequest() {
        return UserFixtures.updateProfile(UserFixtures.profile(UserFixtures.user())).build();
    }

    @Test
    void theOwnerCreatesChangesAndDeletesTheirOwnProfile() {
        UserProfile profile = UserFixtures.profile(UserFixtures.user());
        UUID owner = profile.getUser().getId();
        given(profiles.findOwnerUserIdById(profile.getId())).willReturn(Optional.of(owner));

        assertThat(policy().canCreate(Caller.CUSTOMER.authentication(owner),
                UserFixtures.createProfile(owner).build())).isTrue();
        assertThat(policy().canUpdate(Caller.CUSTOMER.authentication(owner),
                UserFixtures.updateProfile(profile).build())).isTrue();
        assertThat(policy().canDelete(Caller.CUSTOMER.authentication(owner), profile.getId())).isTrue();
        assertThat(policy().canDelete(Caller.CUSTOMER.authentication(), profile.getId())).isFalse();
    }
}
