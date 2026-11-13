package com.triphuddle.api.user.repository;

import com.triphuddle.api.user.entity.UserProfile;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link UserProfile}.
 */
@Repository
public interface UserProfileRepository extends BaseJpaRepository<UserProfile, UUID> {

    /**
     * Find the ID of the user a profile belongs to.
     *
     * @param id the ID of the profile
     * @return the ID of the owning user, or empty if the profile does not exist
     */
    @Query("select up.user.id from UserProfile up where up.id = :id")
    Optional<UUID> findOwnerUserIdById(@Param("id") UUID id);

    /**
     * Check if a user already has a profile.
     *
     * @param userId the ID of the user
     * @return true if the user has a profile, false otherwise
     */
    boolean existsByUserId(UUID userId);
}
