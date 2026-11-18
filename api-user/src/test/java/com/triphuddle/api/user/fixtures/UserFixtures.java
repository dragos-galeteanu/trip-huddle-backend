package com.triphuddle.api.user.fixtures;

import com.triphuddle.api.user.dto.CreateUserPreferenceRequest;
import com.triphuddle.api.user.dto.CreateUserProfileRequest;
import com.triphuddle.api.user.dto.RegisterRequest;
import com.triphuddle.api.user.dto.UpdateUserPreferenceRequest;
import com.triphuddle.api.user.dto.UpdateUserProfileRequest;
import com.triphuddle.api.user.dto.UpdateUserRequest;
import com.triphuddle.api.user.entity.User;
import com.triphuddle.api.user.entity.UserPreference;
import com.triphuddle.api.user.entity.UserProfile;
import com.triphuddle.common.security.UserRole;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Valid users, profiles, preferences and requests. Emails, Firebase UIDs and phone numbers are unique per call.
 */
public final class UserFixtures {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private UserFixtures() {
    }

    public static int next() {
        return SEQ.incrementAndGet();
    }

    public static RegisterRequest.RegisterRequestBuilder register() {
        return RegisterRequest.builder().firstName("Ana").lastName("Pop").phoneNumber(phone(next()));
    }

    public static UpdateUserRequest.UpdateUserRequestBuilder update(User user) {
        return UpdateUserRequest.builder().id(user.getId()).email(user.getEmail()).phoneNumber(user.getPhoneNumber())
                .firstName(user.getFirstName()).lastName(user.getLastName());
    }

    public static User user() {
        int n = next();
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFirebaseUid("firebase-" + n);
        user.setEmail("user" + n + "@example.com");
        user.setPhoneNumber(phone(n));
        user.setFirstName("Ana");
        user.setLastName("Pop");
        user.setRole(UserRole.Customer);
        user.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        user.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return user;
    }

    public static User verifiedUser() {
        User user = user();
        user.setEmailVerified(true);
        return user;
    }

    public static UserProfile profile(User user) {
        UserProfile profile = new UserProfile();
        profile.setId(UUID.randomUUID());
        profile.setUser(user);
        profile.setCountryCode("RO");
        profile.setProfilePictureUrl("https://img.example/p1.jpg");
        profile.setCoverPictureUrl("https://img.example/c1.jpg");
        return profile;
    }

    public static CreateUserProfileRequest.CreateUserProfileRequestBuilder createProfile(UUID userId) {
        return CreateUserProfileRequest.builder().userId(userId).countryCode("RO").bio("Hiker")
                .profilePictureUrl("https://img.example/p1.jpg");
    }

    public static UpdateUserProfileRequest.UpdateUserProfileRequestBuilder updateProfile(UserProfile profile) {
        return UpdateUserProfileRequest.builder().id(profile.getId()).userId(profile.getUser().getId())
                .countryCode(profile.getCountryCode()).profilePictureUrl(profile.getProfilePictureUrl())
                .coverPictureUrl(profile.getCoverPictureUrl()).bio("Changed bio");
    }

    public static UserPreference preference(UUID userId) {
        UserPreference preference = new UserPreference();
        preference.setId(new ObjectId());
        preference.setUserId(userId);
        preference.setLanguageCode("en");
        preference.setCountryCode("RO");
        return preference;
    }

    public static CreateUserPreferenceRequest.CreateUserPreferenceRequestBuilder createPreference(UUID userId) {
        return CreateUserPreferenceRequest.builder().userId(userId).languageCode("en").countryCode("RO")
                .isMarketingOptIn(false);
    }

    public static UpdateUserPreferenceRequest.UpdateUserPreferenceRequestBuilder updatePreference(String id,
                                                                                                  UUID userId) {
        return UpdateUserPreferenceRequest.builder().id(id).userId(userId).languageCode("fr").countryCode("FR")
                .isMarketingOptIn(true);
    }

    private static String phone(int n) {
        return "+4070%07d".formatted(n);
    }
}
