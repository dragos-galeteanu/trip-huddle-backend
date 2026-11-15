package com.triphuddle.api.user.repository;

import com.triphuddle.api.user.entity.UserTravelDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Data access for users' nationalities and residence permits.
 */
public interface UserTravelDocumentRepository extends JpaRepository<UserTravelDocument, UUID> {

    /**
     * Find a user's documents, nationalities first.
     *
     * @param userId the ID of the user
     * @return the documents
     */
    List<UserTravelDocument> findByUserIdOrderByTypeAscCountryCodeAsc(UUID userId);

    /**
     * Remove every document of a user, to replace them or when the user is deleted.
     *
     * @param userId the ID of the user
     * @return how many were removed
     */
    @Modifying
    @Query("delete from UserTravelDocument d where d.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
