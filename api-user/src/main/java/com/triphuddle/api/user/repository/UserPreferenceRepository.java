package com.triphuddle.api.user.repository;

import com.triphuddle.api.user.entity.UserPreference;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Aggregation;
import com.triphuddle.common.repository.BaseMongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPreferenceRepository extends BaseMongoRepository<UserPreference, ObjectId> {

    boolean existsByUserId(UUID userId);

    @Aggregation(pipeline = {
            "{ '$match': { '_id': ?0 } }",
            "{ '$project': { '_id': 0, 'userId': 1 } }"
    })
    Optional<UUID> findOwnerUserIdById(ObjectId id);
}
