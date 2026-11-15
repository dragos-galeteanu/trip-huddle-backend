package com.triphuddle.api.destination.repository;

import com.triphuddle.api.destination.entity.PlaceReview;
import com.triphuddle.common.repository.BaseJpaRepository;
import jakarta.persistence.LockModeType;
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
     * Find a page of the reviews of a place everyone sees, the newest first, after a cursor.
     *
     * @param placeId the ID of the place
     * @param isFirst whether this is the first page, which ignores the cursor
     * @param at the creation time of the previous page's last review
     * @param id the ID of the previous page's last review
     * @param limit how many to take
     * @return the reviews
     */
    @Query("""
            select r from PlaceReview r where r.placeId = :placeId and r.deletedAt is null and r.isHidden = false
              and (:isFirst = true or r.createdAt < :at or (r.createdAt = :at and r.id < :id))
            order by r.createdAt desc, r.id desc""")
    List<PlaceReview> findNewest(@Param("placeId") UUID placeId, @Param("isFirst") boolean isFirst,
                                 @Param("at") Instant at, @Param("id") UUID id, Pageable limit);

    /**
     * Find a page of the reviews of a place everyone sees, the highest rated first, after a cursor.
     *
     * @param placeId the ID of the place
     * @param isFirst whether this is the first page, which ignores the cursor
     * @param rating the rating of the previous page's last review
     * @param at its creation time
     * @param id its ID
     * @param limit how many to take
     * @return the reviews
     */
    @Query("""
            select r from PlaceReview r where r.placeId = :placeId and r.deletedAt is null and r.isHidden = false
              and (:isFirst = true or r.rating < :rating or (r.rating = :rating
                   and (r.createdAt < :at or (r.createdAt = :at and r.id < :id))))
            order by r.rating desc, r.createdAt desc, r.id desc""")
    List<PlaceReview> findHighest(@Param("placeId") UUID placeId, @Param("isFirst") boolean isFirst,
                                  @Param("rating") int rating, @Param("at") Instant at, @Param("id") UUID id,
                                  Pageable limit);

    /**
     * Find a page of the reviews of a place everyone sees, the lowest rated first, after a cursor.
     *
     * @param placeId the ID of the place
     * @param isFirst whether this is the first page, which ignores the cursor
     * @param rating the rating of the previous page's last review
     * @param at its creation time
     * @param id its ID
     * @param limit how many to take
     * @return the reviews
     */
    @Query("""
            select r from PlaceReview r where r.placeId = :placeId and r.deletedAt is null and r.isHidden = false
              and (:isFirst = true or r.rating > :rating or (r.rating = :rating
                   and (r.createdAt < :at or (r.createdAt = :at and r.id < :id))))
            order by r.rating asc, r.createdAt desc, r.id desc""")
    List<PlaceReview> findLowest(@Param("placeId") UUID placeId, @Param("isFirst") boolean isFirst,
                                 @Param("rating") int rating, @Param("at") Instant at, @Param("id") UUID id,
                                 Pageable limit);

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
