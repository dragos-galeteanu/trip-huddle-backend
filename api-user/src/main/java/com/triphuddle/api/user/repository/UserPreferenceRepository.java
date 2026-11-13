package com.triphuddle.api.user.repository;

import com.triphuddle.api.user.entity.UserPreference;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Aggregation;
import com.triphuddle.common.repository.BaseMongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link UserPreference}.
 */
@Repository
public interface UserPreferenceRepository extends BaseMongoRepository<UserPreference, ObjectId> {

    /**
     * Check if a user already has preferences.
     *
     * @param userId the ID of the user
     * @return true if the user has preferences, false otherwise
     */
    boolean existsByUserId(UUID userId);

    /**
     * Find the ID of the user a preference document belongs to.
     *
     * @param id the ID of the preference document
     * @return the ID of the owning user, or empty if the document does not exist
     */
    @Aggregation(pipeline = {
            "{ '$match': { '_id': ?0 } }",
            "{ '$project': { '_id': 0, 'userId': 1 } }"
    })
    Optional<UUID> findOwnerUserIdById(ObjectId id);
}
