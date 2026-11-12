package com.triphuddle.api.user.repository;

import com.triphuddle.api.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    @Query("select up.user.id from UserProfile up where up.id = :id")
    Optional<UUID> findOwnerUserIdById(@Param("id") UUID id);

    boolean existsByUserId(UUID userId);
}
