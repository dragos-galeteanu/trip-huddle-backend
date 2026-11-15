package com.triphuddle.api.destination.repository;

import com.triphuddle.api.destination.entity.PlaceReview;
import com.triphuddle.common.repository.BaseJpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for reviews of places.
 */
public interface PlaceReviewRepository extends BaseJpaRepository<PlaceReview, UUID> {

    /**
     * Find a user's review of a place.
     *
     * @param placeId the ID of the place
     * @param userId the ID of the user
     * @return the review, if any
     */
    Optional<PlaceReview> findByPlaceIdAndUserIdAndDeletedAtIsNull(UUID placeId, UUID userId);

    /**
     * Lock a review.
     *
     * @param id the ID of the review
     * @return the review, if not deleted
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PlaceReview r where r.id = :id and r.deletedAt is null")
    Optional<PlaceReview> lockById(@Param("id") UUID id);

    /**
     * Find the reviews of a place everyone sees, in the page's order.
     *
     * @param placeId the ID of the place
     * @param pageable the page and sort
     * @return a page of reviews
     */
    @Query("select r from PlaceReview r where r.placeId = :placeId and r.deletedAt is null and r.isHidden = false")
    Page<PlaceReview> findVisible(@Param("placeId") UUID placeId, Pageable pageable);

    /**
     * Count a place's visible reviews by rating.
     *
     * @param placeId the ID of the place
     * @return rows of rating and count
     */
    @Query("""
            select r.rating, count(r) from PlaceReview r
            where r.placeId = :placeId and r.deletedAt is null and r.isHidden = false group by r.rating""")
    List<Object[]> countByRating(@Param("placeId") UUID placeId);

    /**
     * Delete every review of a user, when they are deleted.
     *
     * @param userId the ID of the user
     * @param now when
     * @return how many were deleted
     */
    @Modifying
    @Query("update PlaceReview r set r.deletedAt = :now where r.userId = :userId and r.deletedAt is null")
    int deleteByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
