package com.triphuddle.api.destination.repository;

import com.triphuddle.api.destination.entity.TravellerDocuments;
import com.triphuddle.common.repository.BaseJpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for the copies of travellers' nationalities and residence permits.
 */
public interface TravellerDocumentsRepository extends BaseJpaRepository<TravellerDocuments, UUID> {

    /**
     * Lock a user's copy, to apply an update in order.
     *
     * @param userId the ID of the user
     * @return the copy, if any
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from TravellerDocuments d where d.userId = :userId")
    Optional<TravellerDocuments> lockByUserId(@Param("userId") UUID userId);

    /**
     * Find the copies of users not deleted.
     *
     * @param userIds the IDs of the users
     * @return the copies
     */
    List<TravellerDocuments> findByUserIdInAndDeletedAtIsNull(Collection<UUID> userIds);
}
