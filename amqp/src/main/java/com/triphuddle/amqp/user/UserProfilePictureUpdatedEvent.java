package com.triphuddle.amqp.user;

import java.util.UUID;

/**
 * Published when a user's profile picture, cover picture, or both change.
 * A cleared picture is still a change: its URL is then null while its flag is true.
 *
 * @param userId the ID of the user the pictures belong to
 * @param userProfileId the ID of the profile that changed
 * @param profilePictureUrl the profile picture URL after the update, null if cleared
 * @param coverPictureUrl the cover picture URL after the update, null if cleared
 * @param profilePictureChanged whether the profile picture changed
 * @param coverPictureChanged whether the cover picture changed
 */
public record UserProfilePictureUpdatedEvent(
        UUID userId,
        UUID userProfileId,
        String profilePictureUrl,
        String coverPictureUrl,
        boolean profilePictureChanged,
        boolean coverPictureChanged
) {
}
