package com.triphuddle.api.payment.repository;

import com.triphuddle.api.payment.entity.UserContact;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link UserContact}.
 */
@Repository
public interface UserContactRepository extends BaseJpaRepository<UserContact, UUID> {

    /**
     * Find the copy of a user's contact, disabled and deleted ones included.
     *
     * @param userId the ID of the user in api-user
     * @return the copy, if any event about the user arrived
     */
    Optional<UserContact> findByUserId(UUID userId);
}
