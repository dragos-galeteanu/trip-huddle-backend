package com.triphuddle.user.repository;

import com.triphuddle.user.entity.UserPreference;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPreferenceRepository extends MongoRepository<UserPreference, ObjectId> {

    Optional<UserPreference> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
