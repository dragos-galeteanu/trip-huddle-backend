package com.triphuddle.notification.repository;

import com.triphuddle.notification.entity.DeviceToken;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link DeviceToken}.
 */
@Repository
public interface DeviceTokenRepository extends MongoRepository<DeviceToken, ObjectId> {

    /**
     * Find a device by its token.
     *
     * @param token the Firebase Cloud Messaging token
     * @return the device, or empty if the token is not registered
     */
    Optional<DeviceToken> findByToken(String token);

    /**
     * Find the devices of the given users.
     *
     * @param userIds the IDs of the users
     * @return their devices
     */
    List<DeviceToken> findByUserIdIn(Collection<UUID> userIds);

    /**
     * Forget a device of a user.
     *
     * @param userId the ID of the user
     * @param token the Firebase Cloud Messaging token
     */
    void deleteByUserIdAndToken(UUID userId, String token);

    /**
     * Forget the devices with the given tokens, as when FCM reports them as no longer valid.
     *
     * @param tokens the Firebase Cloud Messaging tokens
     */
    void deleteByTokenIn(Collection<String> tokens);
}
