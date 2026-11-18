package com.triphuddle.api.user.service;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.notification.NotificationEmailContract;
import com.triphuddle.amqp.notification.SendEmailRequest;
import com.triphuddle.amqp.user.UserProfilePictureUpdatedContract;
import com.triphuddle.amqp.user.UserProfilePictureUpdatedEvent;
import com.triphuddle.api.user.dto.UserPreferenceResponse;
import com.triphuddle.api.user.dto.UserProfileResponse;
import com.triphuddle.api.user.entity.User;
import com.triphuddle.api.user.entity.UserPreference;
import com.triphuddle.api.user.entity.UserProfile;
import com.triphuddle.api.user.fixtures.UserFixtures;
import com.triphuddle.api.user.mapper.UserPreferenceMapper;
import com.triphuddle.api.user.mapper.UserProfileMapper;
import com.triphuddle.api.user.repository.UserPreferenceRepository;
import com.triphuddle.api.user.repository.UserProfileRepository;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

class UserProfileAndPreferenceServiceTest {

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Profiles {

        @Mock
        UserProfileRepository profiles;

        @Mock
        UserService userService;

        @Mock
        RabbitMQMessageProducer messageProducer;

        @Spy
        UserProfileMapper mapper = Mappers.getMapper(UserProfileMapper.class);

        @InjectMocks
        UserProfileService service;

        private final User user = UserFixtures.user();

        @Test
        void createsTheOnlyProfileOfAUser() {
            given(userService.getEntityById(user.getId())).willReturn(user);
            given(profiles.saveAndFlush(any())).willAnswer(invocation -> {
                UserProfile profile = invocation.getArgument(0);
                profile.setId(UUID.randomUUID());
                return profile;
            });

            UserProfileResponse response = service.create(UserFixtures.createProfile(user.getId()).build());

            assertThat(response.userId()).isEqualTo(user.getId());
            assertThat(response.bio()).isEqualTo("Hiker");
            then(messageProducer).shouldHaveNoInteractions();
        }

        @Test
        void refusesASecondProfile() {
            given(profiles.existsByUserId(user.getId())).willReturn(true);

            assertThatThrownBy(() -> service.create(UserFixtures.createProfile(user.getId()).build()))
                    .isInstanceOf(IllegalStateException.class);
            then(profiles).should(never()).saveAndFlush(any());
        }

        @Test
        void aNewPictureIsAnnouncedAndEmailed() {
            UserProfile profile = givenExisting();

            service.update(UserFixtures.updateProfile(profile).profilePictureUrl("https://img.example/p2.jpg").build());

            then(messageProducer).should().publishAfterCommit(new UserProfilePictureUpdatedEvent(user.getId(),
                    profile.getId(), "https://img.example/p2.jpg", "https://img.example/c1.jpg", true, false), "",
                    UserProfilePictureUpdatedContract.USER_PROFILE_PICTURE_UPDATED_QUEUE_NAME);
            ArgumentCaptor<SendEmailRequest> email = ArgumentCaptor.forClass(SendEmailRequest.class);
            then(messageProducer).should().publishAfterCommit(email.capture(), eq(""),
                    eq(NotificationEmailContract.EMAIL_QUEUE_NAME));
            assertThat(email.getValue().templateData()).containsEntry("profilePictureChanged", true)
                    .containsEntry("coverPictureChanged", false);
        }

        @Test
        void aNewCoverAloneIsAnnounced() {
            UserProfile profile = givenExisting();

            service.update(UserFixtures.updateProfile(profile).coverPictureUrl("https://img.example/c2.jpg").build());

            then(messageProducer).should().publishAfterCommit(new UserProfilePictureUpdatedEvent(user.getId(),
                    profile.getId(), profile.getProfilePictureUrl(), "https://img.example/c2.jpg", false, true), "",
                    UserProfilePictureUpdatedContract.USER_PROFILE_PICTURE_UPDATED_QUEUE_NAME);
        }

        @Test
        void otherChangesAnnounceNothing() {
            UserProfile profile = givenExisting();

            assertThat(service.update(UserFixtures.updateProfile(profile).build()).bio()).isEqualTo("Changed bio");
            then(messageProducer).shouldHaveNoInteractions();
        }

        @Test
        void refusesAnUpdateNamingAnotherUsersProfile() {
            UserProfile profile = givenExisting();

            assertThatThrownBy(() -> service.update(UserFixtures.updateProfile(profile).userId(UUID.randomUUID())
                    .build())).isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void disablesDeletesListsAndHidesDeletedOnes() {
            UserProfile profile = givenExisting();
            given(profiles.findAllByDeletedAtIsNull(PageRequest.of(0, 5))).willReturn(new PageImpl<>(List.of(profile)));

            assertThat(service.disable(profile.getId()).disabledAt()).isNotNull();
            assertThat(service.delete(profile.getId()).deletedAt()).isNotNull();
            assertThat(service.getAll(new PaginationFilter(1, 5)).data()).hasSize(1);
            assertThatThrownBy(() -> service.getById(UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
        }

        private UserProfile givenExisting() {
            UserProfile profile = UserFixtures.profile(user);
            given(profiles.findByIdAndDeletedAtIsNull(profile.getId())).willReturn(Optional.of(profile));
            org.mockito.Mockito.lenient().when(profiles.saveAndFlush(profile)).thenReturn(profile);
            return profile;
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Preferences {

        @Mock
        UserPreferenceRepository preferences;

        @Mock
        UserService userService;

        @Spy
        UserPreferenceMapper mapper = Mappers.getMapper(UserPreferenceMapper.class);

        @InjectMocks
        UserPreferenceService service;

        private final User user = UserFixtures.user();

        @Test
        void createsTheOnlyPreferencesOfAUser() {
            given(userService.getEntityById(user.getId())).willReturn(user);
            given(preferences.save(any())).willAnswer(invocation -> {
                UserPreference preference = invocation.getArgument(0);
                preference.setId(new ObjectId());
                return preference;
            });

            UserPreferenceResponse response = service.create(UserFixtures.createPreference(user.getId()).build());

            assertThat(response.id()).hasSize(24);
            assertThat(response.isMarketingOptIn()).isFalse();
            assertThat(response.languageCode()).isEqualTo("en");
        }

        @Test
        void refusesSecondPreferences() {
            given(preferences.existsByUserId(user.getId())).willReturn(true);

            assertThatThrownBy(() -> service.create(UserFixtures.createPreference(user.getId()).build()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void updatesTheOwnersPreferencesAndRefusesAMismatchedOwner() {
            UserPreference preference = givenExisting();
            String id = preference.getId().toHexString();

            UserPreferenceResponse response = service.update(UserFixtures.updatePreference(id, user.getId()).build());

            assertThat(response.languageCode()).isEqualTo("fr");
            assertThat(response.isMarketingOptIn()).isTrue();
            assertThat(response.id()).as("an update never re-keys the document").isEqualTo(id);
            assertThatThrownBy(() -> service.update(UserFixtures.updatePreference(id, UUID.randomUUID()).build()))
                    .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining(id);
        }

        @Test
        void disablesDeletesListsAndHidesDeletedOnes() {
            UserPreference preference = givenExisting();
            String id = preference.getId().toHexString();
            given(preferences.findAllByDeletedAtIsNull(PageRequest.of(0, 5)))
                    .willReturn(new PageImpl<>(List.of(preference)));

            assertThat(service.disable(id).disabledAt()).isNotNull();
            assertThat(service.delete(id).deletedAt()).isNotNull();
            assertThat(service.getAll(new PaginationFilter(1, 5)).data()).hasSize(1);
            assertThatThrownBy(() -> service.getById(new ObjectId().toHexString()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        private UserPreference givenExisting() {
            UserPreference preference = UserFixtures.preference(user.getId());
            given(preferences.findByIdAndDeletedAtIsNull(preference.getId())).willReturn(Optional.of(preference));
            org.mockito.Mockito.lenient().when(preferences.save(preference)).thenReturn(preference);
            return preference;
        }
    }
}
