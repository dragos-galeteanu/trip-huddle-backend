package com.triphuddle.api.user.security;

import com.triphuddle.api.user.dto.RegisterUserDeviceRequest;
import com.triphuddle.api.user.dto.TravelDocumentsRequest;
import com.triphuddle.api.user.dto.UnregisterUserDeviceRequest;
import com.triphuddle.api.user.fixtures.UserFixtures;
import com.triphuddle.api.user.repository.UserPreferenceRepository;
import com.triphuddle.testsupport.security.Caller;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * The policies of what only its owner ever touches: preferences, push devices and travel documents.
 */
class UserOwnDataPoliciesTest {

    @Nested
    class Preferences {

        private final UserPreferenceRepository repository = mock(UserPreferenceRepository.class);
        private final UserPreferenceAuthorizationPolicy policy = new UserPreferenceAuthorizationPolicy(repository);
        private final UUID owner = UUID.randomUUID();
        private final String id = new ObjectId().toHexString();

        @Test
        void theOwnerDoesEverythingWithTheirOwn() {
            given(repository.findOwnerUserIdById(new ObjectId(id))).willReturn(Optional.of(owner));
            Authentication me = Caller.CUSTOMER.authentication(owner);

            assertThat(policy.canCreate(me, UserFixtures.createPreference(owner).build())).isTrue();
            assertThat(policy.canGetById(me, id)).isTrue();
            assertThat(policy.canUpdate(me, UserFixtures.updatePreference(id, owner).build())).isTrue();
            assertThat(policy.canDelete(me, id)).isTrue();
            assertThat(policy.canGetAll(me)).isFalse();
        }

        @Test
        void otherCustomersGetNothingStaffReadAndAdminsDelete() {
            given(repository.findOwnerUserIdById(new ObjectId(id))).willReturn(Optional.of(owner));
            Authentication stranger = Caller.CUSTOMER.authentication();

            assertThat(policy.canCreate(stranger, UserFixtures.createPreference(owner).build())).isFalse();
            assertThat(policy.canGetById(stranger, id)).isFalse();
            assertThat(policy.canGetById(Caller.INSPECTOR.authentication(), id)).isTrue();
            assertThat(policy.canGetAll(Caller.INSPECTOR.authentication())).isTrue();
            assertThat(policy.canUpdate(Caller.SUPPORT.authentication(),
                    UserFixtures.updatePreference(id, owner).build())).isTrue();
            assertThat(policy.canDelete(Caller.SUPPORT.authentication(), id)).isFalse();
            assertThat(policy.canDelete(Caller.ADMIN.authentication(), id)).isTrue();
        }

        @Test
        void aMalformedOrMissingIdHasNoOwner() {
            assertThat(policy.resolveOwnerUserId("not-an-object-id")).isEmpty();
            assertThat(policy.resolveOwnerUserId(null)).isEmpty();
        }
    }

    @Nested
    class DevicesAndTravelDocuments {

        private final UserDeviceAuthorizationPolicy devices = new UserDeviceAuthorizationPolicy();
        private final UserTravelDocumentAuthorizationPolicy documents = new UserTravelDocumentAuthorizationPolicy();

        @ParameterizedTest
        @EnumSource(value = Caller.class, names = "ANONYMOUS", mode = EnumSource.Mode.EXCLUDE)
        void anySignedInUserManagesTheirOwnAndNothingElse(Caller caller) {
            Authentication me = caller.authentication();

            assertThat(devices.canCreate(me, new RegisterUserDeviceRequest())).isTrue();
            assertThat(devices.canUpdate(me, new UnregisterUserDeviceRequest())).isTrue();
            assertThat(documents.canGetMine(me)).isTrue();
            assertThat(documents.canUpdate(me, new TravelDocumentsRequest())).isTrue();
            assertThat(devices.canGetById(me, "x") || devices.canGetAll(me) || devices.canDelete(me, "x")
                    || devices.canDisable(me)).isFalse();
            assertThat(documents.canCreate(me, null) || documents.canGetById(me, "x") || documents.canGetAll(me)
                    || documents.canDelete(me, "x") || documents.canDisable(me)).isFalse();
        }

        @Test
        void nobodyAnonymous() {
            Authentication anonymous = Caller.ANONYMOUS.authentication();

            assertThat(devices.canCreate(anonymous, new RegisterUserDeviceRequest())).isFalse();
            assertThat(documents.canGetMine(anonymous)).isFalse();
        }
    }
}
