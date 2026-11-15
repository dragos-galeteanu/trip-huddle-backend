package com.triphuddle.api.destination.repository;

import java.util.List;
import com.triphuddle.api.destination.entity.PlaceQuestion;
import com.triphuddle.common.repository.BaseJpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for questions about places.
 */
public interface PlaceQuestionRepository extends BaseJpaRepository<PlaceQuestion, UUID> {

    /**
     * Find a page of a user's questions about a place, the newest first, after a cursor.
     *
     * @param placeId the ID of the place
     * @param userId the ID of the user
     * @param isFirst whether this is the first page, which ignores the cursor
     * @param at the time the previous page's last question was asked
     * @param id its ID
     * @param limit how many to take
     * @return the questions
     */
    @Query("""
            select q from PlaceQuestion q where q.placeId = :placeId and q.userId = :userId and q.deletedAt is null
              and (:isFirst = true or q.requestedAt < :at or (q.requestedAt = :at and q.id < :id))
            order by q.requestedAt desc, q.id desc""")
    List<PlaceQuestion> findMine(@Param("placeId") UUID placeId, @Param("userId") UUID userId,
                                 @Param("isFirst") boolean isFirst, @Param("at") Instant at, @Param("id") UUID id,
                                 Pageable limit);

    /**
     * Lock the question waiting for an answer.
     *
     * @param jobId the ID of the job
     * @return the question, if still waiting
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from PlaceQuestion q where q.pendingJobId = :jobId")
    Optional<PlaceQuestion> lockByPendingJobId(@Param("jobId") UUID jobId);

    /**
     * Give up on questions whose answer is overdue.
     *
     * @param requestedBefore questions asked before this are overdue
     * @return how many were given up on
     */
    @Modifying
    @Query("""
            update PlaceQuestion q set q.pendingJobId = null, q.error = 'The answer took too long; ask again',
                q.status = com.triphuddle.api.destination.entity.ContentStatus.FAILED
            where q.pendingJobId is not null and q.requestedAt < :requestedBefore""")
    int giveUpOverdue(@Param("requestedBefore") Instant requestedBefore);

    /**
     * Delete every question of a user, when they are deleted.
     *
     * @param userId the ID of the user
     * @param now when
     * @return how many were deleted
     */
    @Modifying
    @Query("""
            update PlaceQuestion q set q.deletedAt = :now, q.question = '', q.answer = null
            where q.userId = :userId and q.deletedAt is null""")
    int deleteByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
