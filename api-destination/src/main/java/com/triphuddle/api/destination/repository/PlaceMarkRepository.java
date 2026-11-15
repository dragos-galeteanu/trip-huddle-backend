package com.triphuddle.api.destination.repository;

import com.triphuddle.api.destination.entity.PlaceMark;
import com.triphuddle.api.destination.entity.PlaceMarkKind;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for users' favourite and visited places.
 */
public interface PlaceMarkRepository extends BaseJpaRepository<PlaceMark, UUID> {

    /**
     * Find a user's mark of one kind on a place.
     *
     * @param userId the ID of the user
     * @param placeId the ID of the place
     * @param kind the kind
     * @return the mark, if any
     */
    Optional<PlaceMark> findByUserIdAndPlaceIdAndKind(UUID userId, UUID placeId, PlaceMarkKind kind);

    /**
     * Find a user's marks of one kind on listed places, the newest first.
     *
     * @param userId the ID of the user
     * @param kind the kind
     * @param pageable the page
     * @return a page of marks
     */
    @Query("""
            select m from PlaceMark m where m.userId = :userId and m.kind = :kind and exists (
                select 1 from Place p where p.id = m.placeId and p.deletedAt is null
                    and p.status = com.triphuddle.api.destination.entity.PlaceStatus.ACTIVE)
            order by m.createdAt desc""")
    Page<PlaceMark> findListed(@Param("userId") UUID userId, @Param("kind") PlaceMarkKind kind, Pageable pageable);

    /**
     * Find a user's marks on some places.
     *
     * @param userId the ID of the user
     * @param placeIds the IDs of the places
     * @return the marks
     */
    List<PlaceMark> findByUserIdAndPlaceIdIn(UUID userId, Collection<UUID> placeIds);

    /**
     * Remove every mark of a user, when they are deleted.
     *
     * @param userId the ID of the user
     * @return how many were removed
     */
    @Modifying
    @Query("delete from PlaceMark m where m.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
