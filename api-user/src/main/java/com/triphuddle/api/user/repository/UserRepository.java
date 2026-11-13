package com.triphuddle.api.user.repository;

import com.triphuddle.api.user.entity.User;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link User}.
 */
@Repository
public interface UserRepository extends BaseJpaRepository<User, UUID> {

    /**
     * Check if a Firebase account is already registered.
     *
     * @param firebaseUid the Firebase UID to look for
     * @return true if a user has the Firebase UID, false otherwise
     */
    boolean existsByFirebaseUid(String firebaseUid);

    /**
     * Find a user by their Firebase UID.
     *
     * @param firebaseUid the Firebase UID to look for
     * @return the user, or empty if none has it
     */
    Optional<User> findByFirebaseUid(String firebaseUid);

    /**
     * Find the user an email verification token was issued to.
     *
     * @param emailVerificationToken the token to look for
     * @return the user, or empty if none has it
     */
    Optional<User> findByEmailVerificationToken(UUID emailVerificationToken);

    /**
     * Find a user by their exact email address.
     *
     * @param email the email address to look for
     * @return the user, or empty if none has it
     */
    Optional<User> findByEmail(String email);

    /**
     * Find a user by their email address, ignoring case.
     *
     * @param email the email address to look for
     * @return the user, or empty if none has it
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Find a batch of users whose role differs from the one last sent to Firebase.
     *
     * @param pageable the batch to return
     * @return a slice of the users to sync
     */
    @Query("SELECT u FROM User u WHERE u.firebaseSyncedRole IS NULL OR u.firebaseSyncedRole <> u.role")
    Slice<User> findUsersWithUnsyncedRoles(Pageable pageable);
}
