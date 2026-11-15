package com.triphuddle.api.destination.repository;

import com.triphuddle.api.destination.entity.ReviewReport;
import com.triphuddle.api.destination.entity.ReviewReportStatus;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.data.domain.Page;
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
     * Find reports for staff, open or settled, the oldest open first or the newest settled first.
     *
     * @param isOpen whether to find the open ones
     * @param pageable the page
     * @return a page of reports, with their reviews
     */
    @Query(value = """
            select r from ReviewReport r join fetch r.review
            where (:isOpen = true and r.status = com.triphuddle.api.destination.entity.ReviewReportStatus.OPEN)
               or (:isOpen = false and r.status <> com.triphuddle.api.destination.entity.ReviewReportStatus.OPEN)
            order by case when :isOpen = true then r.createdAt end asc, r.resolvedAt desc""",
            countQuery = """
            select count(r) from ReviewReport r
            where (:isOpen = true and r.status = com.triphuddle.api.destination.entity.ReviewReportStatus.OPEN)
               or (:isOpen = false and r.status <> com.triphuddle.api.destination.entity.ReviewReportStatus.OPEN)""")
    Page<ReviewReport> findForStaff(@Param("isOpen") boolean isOpen, Pageable pageable);
}
