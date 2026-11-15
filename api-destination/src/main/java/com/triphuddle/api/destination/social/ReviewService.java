package com.triphuddle.api.destination.social;

import com.triphuddle.api.destination.config.SocialProperties;
import com.triphuddle.api.destination.content.ContentService;
import com.triphuddle.api.destination.dto.ReviewReportResponse;
import com.triphuddle.api.destination.dto.ReviewRequest;
import com.triphuddle.api.destination.dto.ReviewResponse;
import com.triphuddle.api.destination.dto.ReviewSummaryResponse;
import com.triphuddle.api.destination.entity.PlaceReview;
import com.triphuddle.api.destination.entity.ReviewReport;
import com.triphuddle.api.destination.entity.ReviewReportStatus;
import com.triphuddle.api.destination.repository.PlaceReviewRepository;
import com.triphuddle.api.destination.repository.ReviewReportRepository;
import com.triphuddle.api.destination.service.PlaceQueryService;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reviews of places: one per user and place, shown to everyone unless reported enough or hidden by staff.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReviewService {

    /**
     * How a place's reviews are ordered.
     */
    public enum ReviewSort { NEWEST, HIGHEST, LOWEST }

    private final PlaceReviewRepository reviewRepository;
    private final ReviewReportRepository reportRepository;
    private final PlaceQueryService placeQueryService;
    private final SocialProperties properties;
    private final Clock clock;

    /**
     * Write or rewrite the caller's review of a place.
     *
     * @param placeId the ID of the place
     * @param userId the author
     * @param request the review
     * @return the review
     */
    public ReviewResponse upsertMine(UUID placeId, UUID userId, ReviewRequest request) {
        UUID target = placeQueryService.visiblePlace(placeId, false).getId();
        PlaceReview review = reviewRepository.findByPlaceIdAndUserIdAndDeletedAtIsNull(target, userId)
                .orElseGet(() -> {
                    PlaceReview created = new PlaceReview();
                    created.setPlaceId(target);
                    created.setUserId(userId);
                    return created;
                });
        review.setRating(request.getRating());
        review.setTitle(blankToNull(request.getTitle()));
        review.setBody(blankToNull(request.getBody()));
        review.setVisitedOn(request.getVisitedOn());
        review.setLanguageCode(ContentService.normalise(request.getLanguage()));
        // A hidden review stays hidden when its author changes it; staff decide on it.
        return toResponse(reviewRepository.save(review));
    }

    /**
     * Delete the caller's review of a place, so they may write a new one.
     *
     * @param placeId the ID of the place
     * @param userId the author
     */
    public void deleteMine(UUID placeId, UUID userId) {
        reviewRepository.findByPlaceIdAndUserIdAndDeletedAtIsNull(placeId, userId)
                .ifPresent(review -> review.setDeletedAt(clock.instant()));
    }

    /**
     * Get the caller's review of a place, hidden or not.
     *
     * @param placeId the ID of the place
     * @param userId the author
     * @return the review
     */
    @Transactional(readOnly = true)
    public ReviewResponse getMine(UUID placeId, UUID userId) {
        return reviewRepository.findByPlaceIdAndUserIdAndDeletedAtIsNull(placeId, userId).map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("You haven't reviewed this place"));
    }

    /**
     * Get the reviews of a place everyone sees.
     *
     * @param placeId the ID of the place
     * @param sort the order
     * @param filter the page
     * @return a page of reviews
     */
    @Transactional(readOnly = true)
    public Paginated<ReviewResponse> getForPlace(UUID placeId, ReviewSort sort, PaginationFilter filter) {
        UUID target = placeQueryService.visiblePlace(placeId, false).getId();
        Sort order = switch (sort == null ? ReviewSort.NEWEST : sort) {
            case NEWEST -> Sort.by(Sort.Order.desc("createdAt"));
            case HIGHEST -> Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("createdAt"));
            case LOWEST -> Sort.by(Sort.Order.asc("rating"), Sort.Order.desc("createdAt"));
        };
        return Paginated.of(reviewRepository.findVisible(target,
                        PageRequest.of(filter.toPageable().getPageNumber(), filter.toPageable().getPageSize(), order)),
                reviews -> reviews.stream().map(this::toPublicResponse).toList());
    }

    /**
     * Get what a place's visible reviews add up to.
     *
     * @param placeId the ID of the place
     * @return the summary
     */
    @Transactional(readOnly = true)
    public ReviewSummaryResponse summaryOf(UUID placeId) {
        UUID target = placeQueryService.visiblePlace(placeId, false).getId();
        Map<Integer, Long> byRating = new LinkedHashMap<>();
        for (int rating = 1; rating <= 5; rating++) {
            byRating.put(rating, 0L);
        }
        for (Object[] row : reviewRepository.countByRating(target)) {
            byRating.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        long count = byRating.values().stream().mapToLong(Long::longValue).sum();
        long total = byRating.entrySet().stream().mapToLong(entry -> entry.getKey() * entry.getValue()).sum();
        Double average = count == 0 ? null : Math.round(total * 10.0 / count) / 10.0;
        return new ReviewSummaryResponse(target, count, average, byRating);
    }

    /**
     * Report a review; enough open reports hide it until staff look.
     *
     * @param reviewId the ID of the review
     * @param userId who reports it
     * @param reason why
     * @return the report
     */
    public ReviewReportResponse report(UUID reviewId, UUID userId, String reason) {
        PlaceReview review = reviewRepository.lockById(reviewId).filter(found -> !found.isHidden())
                .orElseThrow(() -> new ResourceNotFoundException("Review with id=%s not found".formatted(reviewId)));
        if (review.getUserId().equals(userId)) {
            throw new IllegalStateException("You can't report your own review");
        }
        if (reportRepository.existsByReviewIdAndUserId(reviewId, userId)) {
            throw new IllegalStateException("You already reported this review");
        }
        ReviewReport report = new ReviewReport();
        report.setReview(review);
        report.setUserId(userId);
        report.setReason(reason.strip());
        report.setStatus(ReviewReportStatus.OPEN);
        reportRepository.save(report);
        review.setOpenReportCount(review.getOpenReportCount() + 1);
        if (review.getOpenReportCount() >= properties.reviewAutoHideReports()) {
            hide(review, "Hidden after reports, waiting for staff", null);
            log.info("Review hidden after reports [id={}, reports={}]", reviewId, review.getOpenReportCount());
        }
        return toResponse(report);
    }

    /**
     * Get reports for staff.
     *
     * @param isOpen whether to get the open ones
     * @param filter the page
     * @return a page of reports
     */
    @Transactional(readOnly = true)
    public Paginated<ReviewReportResponse> getReports(boolean isOpen, PaginationFilter filter) {
        return Paginated.of(reportRepository.findForStaff(isOpen, filter.toPageable()),
                reports -> reports.stream().map(this::toResponse).toList());
    }

    /**
     * Settle every open report of a review by keeping or hiding it.
     *
     * @param reportId the ID of one of its reports
     * @param isHiding whether to hide the review
     * @param staffId the staff member
     * @return the report
     */
    public ReviewReportResponse resolve(UUID reportId, boolean isHiding, UUID staffId) {
        ReviewReport report = reportRepository.findByIdAndDeletedAtIsNull(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report with id=%s not found".formatted(reportId)));
        PlaceReview review = reviewRepository.lockById(report.getReview().getId())
                .orElseThrow(() -> new ResourceNotFoundException("The review was deleted"));
        Instant now = clock.instant();
        for (ReviewReport open : reportRepository.findByReviewIdAndStatus(review.getId(), ReviewReportStatus.OPEN)) {
            open.setStatus(isHiding ? ReviewReportStatus.HIDDEN : ReviewReportStatus.KEPT);
            open.setResolvedByUserId(staffId);
            open.setResolvedAt(now);
        }
        review.setOpenReportCount(0);
        if (isHiding) {
            hide(review, "Hidden by staff after reports", staffId);
        } else {
            show(review);
        }
        log.info("Review reports settled [reviewId={}, isHiding={}, staffId={}]", review.getId(), isHiding, staffId);
        return toResponse(report);
    }

    /**
     * Hide or show a review, as staff.
     *
     * @param reviewId the ID of the review
     * @param isHiding whether to hide it
     * @param reason why, when hiding
     * @param staffId the staff member
     * @return the review
     */
    public ReviewResponse moderate(UUID reviewId, boolean isHiding, String reason, UUID staffId) {
        PlaceReview review = reviewRepository.lockById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review with id=%s not found".formatted(reviewId)));
        if (isHiding) {
            hide(review, reason == null || reason.isBlank() ? "Hidden by staff" : reason.strip(), staffId);
        } else {
            show(review);
        }
        return toResponse(review);
    }

    /**
     * Delete every review of a deleted user.
     *
     * @param userId the ID of the user
     * @return how many were deleted
     */
    public int deleteForUser(UUID userId) {
        return reviewRepository.deleteByUserId(userId, clock.instant());
    }

    private void hide(PlaceReview review, String reason, UUID staffId) {
        review.setHidden(true);
        review.setHiddenReason(reason);
        review.setHiddenAt(clock.instant());
        review.setHiddenByUserId(staffId);
    }

    private static void show(PlaceReview review) {
        review.setHidden(false);
        review.setHiddenReason(null);
        review.setHiddenAt(null);
        review.setHiddenByUserId(null);
    }

    private ReviewResponse toResponse(PlaceReview review) {
        return new ReviewResponse(review.getId(), review.getPlaceId(), review.getUserId(), review.getRating(),
                review.getTitle(), review.getBody(), review.getVisitedOn(), review.getLanguageCode(),
                review.isHidden(), review.getHiddenReason(), review.getCreatedAt(), review.getUpdatedAt());
    }

    private ReviewResponse toPublicResponse(PlaceReview review) {
        return new ReviewResponse(review.getId(), review.getPlaceId(), review.getUserId(), review.getRating(),
                review.getTitle(), review.getBody(), review.getVisitedOn(), review.getLanguageCode(), false, null,
                review.getCreatedAt(), review.getUpdatedAt());
    }

    private ReviewReportResponse toResponse(ReviewReport report) {
        return new ReviewReportResponse(report.getId(), toResponse(report.getReview()), report.getUserId(),
                report.getReason(), report.getStatus(), report.getCreatedAt(), report.getResolvedByUserId(),
                report.getResolvedAt());
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
