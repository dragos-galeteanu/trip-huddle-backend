package com.triphuddle.user.repository;

import com.triphuddle.user.entity.UserPreference;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPreferenceRepository extends MongoRepository<UserPreference, ObjectId> {

    boolean existsByUserId(UUID userId);

    @Aggregation(pipeline = {
            "{ '$match': { '_id': ?0 } }",
            "{ '$project': { '_id': 0, 'userId': 1 } }"
    })
    Optional<UUID> findOwnerUserIdById(ObjectId id);
}
