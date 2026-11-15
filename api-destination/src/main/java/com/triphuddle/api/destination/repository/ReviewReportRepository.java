package com.triphuddle.api.destination.repository;

import java.time.Instant;
import com.triphuddle.api.destination.entity.ReviewReport;
import com.triphuddle.api.destination.entity.ReviewReportStatus;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Data access for reports of reviews.
 */
public interface ReviewReportRepository extends BaseJpaRepository<ReviewReport, UUID> {

    /**
     * Check if a user already reported a review.
     *
     * @param reviewId the ID of the review
     * @param userId the ID of the user
     * @return true if they did
     */
    boolean existsByReviewIdAndUserId(UUID reviewId, UUID userId);

    /**
     * Find a review's reports in a status.
     *
     * @param reviewId the ID of the review
     * @param status e.g. OPEN
     * @return the reports
     */
    List<ReviewReport> findByReviewIdAndStatus(UUID reviewId, ReviewReportStatus status);

    /**
     * Find a page of open reports, the oldest first, after a cursor; reports settled meanwhile drop out without
     * shifting the pages.
     *
     * @param isFirst whether this is the first page, which ignores the cursor
     * @param at the creation time of the previous page's last report
     * @param id its ID
     * @param limit how many to take
     * @return the reports, with their reviews
     */
    @Query("""
            select r from ReviewReport r join fetch r.review
            where r.status = com.triphuddle.api.destination.entity.ReviewReportStatus.OPEN
              and (:isFirst = true or r.createdAt > :at or (r.createdAt = :at and r.id > :id))
            order by r.createdAt asc, r.id asc""")
    List<ReviewReport> findOpen(@Param("isFirst") boolean isFirst, @Param("at") Instant at, @Param("id") UUID id,
                                Pageable limit);

    /**
     * Find a page of settled reports, the most recently settled first, after a cursor.
     *
     * @param isFirst whether this is the first page, which ignores the cursor
     * @param at the settling time of the previous page's last report
     * @param id its ID
     * @param limit how many to take
     * @return the reports, with their reviews
     */
    @Query("""
            select r from ReviewReport r join fetch r.review
            where r.status <> com.triphuddle.api.destination.entity.ReviewReportStatus.OPEN
              and (:isFirst = true or r.resolvedAt < :at or (r.resolvedAt = :at and r.id < :id))
            order by r.resolvedAt desc, r.id desc""")
    List<ReviewReport> findSettled(@Param("isFirst") boolean isFirst, @Param("at") Instant at,
                                   @Param("id") UUID id, Pageable limit);
}
