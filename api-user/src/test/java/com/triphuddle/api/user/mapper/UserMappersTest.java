package com.triphuddle.api.user.mapper;

import com.triphuddle.api.user.dto.DetailedUserResponse;
import com.triphuddle.api.user.entity.User;
import com.triphuddle.api.user.entity.UserProfile;
import com.triphuddle.api.user.fixtures.UserFixtures;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserMappersTest {

    private final UserMapper users = Mappers.getMapper(UserMapper.class);
    private final UserProfileMapper profiles = Mappers.getMapper(UserProfileMapper.class);

    @Test
    void theDetailedFormCarriesTheContactDetails() {
        User user = UserFixtures.verifiedUser();

        DetailedUserResponse detailed = users.toDetailedUserResponse(user);

        assertThat(detailed.email()).isEqualTo(user.getEmail());
        assertThat(detailed.phoneNumber()).isEqualTo(user.getPhoneNumber());
        assertThat(detailed.firebaseUid()).isEqualTo(user.getFirebaseUid());
        assertThat(detailed.emailVerified()).isTrue();
    }

    @Test
    void thePublicFormHasNoContactDetails() {
        assertThat(java.util.Arrays.stream(com.triphuddle.api.user.dto.UserResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .doesNotContain("email", "phoneNumber", "firebaseUid");
    }

    @Test
    void aProfileNamesItsUserAndItsXLink() {
        UserProfile profile = UserFixtures.profile(UserFixtures.user());
        profile.setXUrl("https://x.com/ana");

        assertThat(profiles.toResponse(profile).userId()).isEqualTo(profile.getUser().getId());
        assertThat(profiles.toResponse(profile).xUrl()).isEqualTo("https://x.com/ana");
    }

    @Test
    void updatesNeverReKeyTheRow() {
        User user = UserFixtures.user();
        UUID id = user.getId();
        users.updateEntityFromRequest(user, UserFixtures.update(user).id(UUID.randomUUID()).firstName("Ioana").build());

        UserProfile profile = UserFixtures.profile(user);
        UUID profileId = profile.getId();
        profiles.updateEntityFromRequest(profile, UserFixtures.updateProfile(profile).id(UUID.randomUUID()).build());

        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getFirstName()).isEqualTo("Ioana");
        assertThat(profile.getId()).isEqualTo(profileId);
        assertThat(profile.getUser()).isSameAs(user);
    }

    @Test
    void aRegisterRequestMapsOnlyTheNames() {
        User user = new User();
        users.createEntityFromRequest(user, UserFixtures.register().firstName("Ana").build());

        assertThat(user.getFirstName()).isEqualTo("Ana");
        assertThat(user.getId()).isNull();
    }
}
