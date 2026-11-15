package com.triphuddle.api.destination.controller;

import com.triphuddle.api.destination.dto.MarkedPlaceResponse;
import com.triphuddle.api.destination.dto.PlaceMarkResponse;
import com.triphuddle.api.destination.dto.QuestionRequest;
import com.triphuddle.api.destination.dto.QuestionResponse;
import com.triphuddle.api.destination.dto.ReviewReportRequest;
import com.triphuddle.api.destination.dto.ReviewReportResponse;
import com.triphuddle.api.destination.dto.ReviewRequest;
import com.triphuddle.api.destination.dto.ReviewResponse;
import com.triphuddle.api.destination.dto.ReviewSummaryResponse;
import com.triphuddle.api.destination.dto.VisitedRequest;
import com.triphuddle.api.destination.entity.PlaceMarkKind;
import com.triphuddle.api.destination.security.DestinationAuthorizationPolicy;
import com.triphuddle.api.destination.social.PlaceMarkService;
import com.triphuddle.api.destination.social.QuestionService;
import com.triphuddle.api.destination.social.ReviewService;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Favourites, visited places, reviews and questions about places, under {@code /api/v1/destinations}.
 */
@RestController
@RequestMapping("/api/v1/destinations")
@RequiredArgsConstructor
@Tag(name = "Favourites, reviews and questions")
public class SocialController {

    private final PlaceMarkService markService;
    private final ReviewService reviewService;
    private final QuestionService questionService;
    private final DestinationAuthorizationPolicy destinationPolicy;

    /**
     * Save a place as a favourite.
     *
     * @param placeId the ID of the place
     * @param groupId the group the caller acts for, or null
     * @param authentication the caller
     * @return the mark
     */
    @PutMapping("/places/{placeId}/favourite")
    @Operation(summary = "Save a favourite")
    @PreAuthorize("@DestinationPolicy.canBrowse(authentication, #groupId)")
    public ResponseEntity<PlaceMarkResponse> favourite(@PathVariable UUID placeId,
                                                       @RequestParam(required = false) UUID groupId,
                                                       Authentication authentication) {
        return ResponseEntity.ok(markService.mark(SecurityUtils.extractUserId(authentication), placeId,
                PlaceMarkKind.FAVOURITE, null));
    }

    /**
     * Remove a place from the favourites.
     *
     * @param placeId the ID of the place
     * @param authentication the caller
     * @return no content
     */
    @DeleteMapping("/places/{placeId}/favourite")
    @Operation(summary = "Remove a favourite")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> unfavourite(@PathVariable UUID placeId, Authentication authentication) {
        markService.unmark(SecurityUtils.extractUserId(authentication), placeId, PlaceMarkKind.FAVOURITE);
        return ResponseEntity.noContent().build();
    }

    /**
     * Mark a place as visited, optionally with when.
     *
     * @param placeId the ID of the place
     * @param request when, or nothing
     * @param groupId the group the caller acts for, or null
     * @param authentication the caller
     * @return the mark
     */
    @PutMapping("/places/{placeId}/visited")
    @Operation(summary = "Mark as visited")
    @PreAuthorize("@DestinationPolicy.canBrowse(authentication, #groupId)")
    public ResponseEntity<PlaceMarkResponse> visited(@PathVariable UUID placeId,
                                                     @Valid @RequestBody(required = false) VisitedRequest request,
                                                     @RequestParam(required = false) UUID groupId,
                                                     Authentication authentication) {
        return ResponseEntity.ok(markService.mark(SecurityUtils.extractUserId(authentication), placeId,
                PlaceMarkKind.VISITED, request == null ? null : request.getVisitedOn()));
    }

    /**
     * Unmark a place as visited.
     *
     * @param placeId the ID of the place
     * @param authentication the caller
     * @return no content
     */
    @DeleteMapping("/places/{placeId}/visited")
    @Operation(summary = "Unmark as visited")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> unvisited(@PathVariable UUID placeId, Authentication authentication) {
        markService.unmark(SecurityUtils.extractUserId(authentication), placeId, PlaceMarkKind.VISITED);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get the caller's favourites or visited places, the newest first.
     *
     * @param kind FAVOURITE or VISITED
     * @param filter the page
     * @param groupId the group the caller acts for, or null
     * @param authentication the caller
     * @return a page of places
     */
    @GetMapping("/me/places")
    @Operation(summary = "Get my favourites or visited places")
    @PreAuthorize("@DestinationPolicy.canBrowse(authentication, #groupId)")
    public ResponseEntity<Paginated<MarkedPlaceResponse>> myPlaces(@RequestParam PlaceMarkKind kind,
                                                                   @Valid @ModelAttribute PaginationFilter filter,
                                                                   @RequestParam(required = false) UUID groupId,
                                                                   Authentication authentication) {
        return ResponseEntity.ok(markService.getMine(SecurityUtils.extractUserId(authentication), kind, filter));
    }

    /**
     * Get the caller's marks on some places, to show them on a list or map.
     *
     * @param placeIds the IDs of the places, at most 200
     * @param authentication the caller
     * @return the marks there are
     */
    @GetMapping("/me/place-marks")
    @Operation(summary = "Get my marks on places")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PlaceMarkResponse>> myMarks(@RequestParam @Size(max = 200) List<UUID> placeIds,
                                                           Authentication authentication) {
        return ResponseEntity.ok(markService.marksOn(SecurityUtils.extractUserId(authentication), placeIds));
    }

    /**
     * Get a place's reviews.
     *
     * @param placeId the ID of the place
     * @param sort NEWEST, HIGHEST or LOWEST
     * @param filter the page
     * @param groupId the group the caller acts for, or null
     * @return a page of reviews
     */
    @GetMapping("/places/{placeId}/reviews")
    @Operation(summary = "Get a place's reviews")
    @PreAuthorize("@DestinationPolicy.canBrowse(authentication, #groupId)")
    public ResponseEntity<Paginated<ReviewResponse>> reviews(@PathVariable UUID placeId,
                                                             @RequestParam(required = false)
                                                             ReviewService.ReviewSort sort,
                                                             @Valid @ModelAttribute PaginationFilter filter,
                                                             @RequestParam(required = false) UUID groupId) {
        return ResponseEntity.ok(reviewService.getForPlace(placeId, sort, filter));
    }

    /**
     * Get what a place's reviews add up to.
     *
     * @param placeId the ID of the place
     * @param groupId the group the caller acts for, or null
     * @return the summary
     */
    @GetMapping("/places/{placeId}/reviews/summary")
    @Operation(summary = "Get a place's rating")
    @PreAuthorize("@DestinationPolicy.canBrowse(authentication, #groupId)")
    public ResponseEntity<ReviewSummaryResponse> reviewSummary(@PathVariable UUID placeId,
                                                               @RequestParam(required = false) UUID groupId) {
        return ResponseEntity.ok(reviewService.summaryOf(placeId));
    }

    /**
     * Get the caller's review of a place, hidden or not.
     *
     * @param placeId the ID of the place
     * @param authentication the caller
     * @return the review
     */
    @GetMapping("/places/{placeId}/reviews/mine")
    @Operation(summary = "Get my review")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReviewResponse> myReview(@PathVariable UUID placeId, Authentication authentication) {
        return ResponseEntity.ok(reviewService.getMine(placeId, SecurityUtils.extractUserId(authentication)));
    }

    /**
     * Write or rewrite the caller's review of a place.
     *
     * @param placeId the ID of the place
     * @param request the review
     * @param groupId the group the caller acts for, or null
     * @param authentication the caller
     * @return the review
     */
    @PutMapping("/places/{placeId}/reviews/mine")
    @Operation(summary = "Write my review")
    @PreAuthorize("@DestinationPolicy.canBrowse(authentication, #groupId)")
    public ResponseEntity<ReviewResponse> review(@PathVariable UUID placeId, @Valid @RequestBody ReviewRequest request,
                                                 @RequestParam(required = false) UUID groupId,
                                                 Authentication authentication) {
        return ResponseEntity.ok(reviewService.upsertMine(placeId, SecurityUtils.extractUserId(authentication),
                request));
    }

    /**
     * Delete the caller's review of a place.
     *
     * @param placeId the ID of the place
     * @param authentication the caller
     * @return no content
     */
    @DeleteMapping("/places/{placeId}/reviews/mine")
    @Operation(summary = "Delete my review")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteReview(@PathVariable UUID placeId, Authentication authentication) {
        reviewService.deleteMine(placeId, SecurityUtils.extractUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    /**
     * Report a review as wrong or abusive.
     *
     * @param reviewId the ID of the review
     * @param request why
     * @param groupId the group the caller acts for, or null
     * @param authentication the caller
     * @return the report
     */
    @PostMapping("/reviews/{reviewId}/report")
    @Operation(summary = "Report a review")
    @PreAuthorize("@DestinationPolicy.canBrowse(authentication, #groupId)")
    public ResponseEntity<ReviewReportResponse> report(@PathVariable UUID reviewId,
                                                       @Valid @RequestBody ReviewReportRequest request,
                                                       @RequestParam(required = false) UUID groupId,
                                                       Authentication authentication) {
        return new ResponseEntity<>(reviewService.report(reviewId, SecurityUtils.extractUserId(authentication),
                request.getReason()), HttpStatus.CREATED);
    }

    /**
     * Hide or show a review.
     *
     * @param reviewId the ID of the review
     * @param isHiding whether to hide it
     * @param reason why, when hiding
     * @param authentication the caller
     * @return the review
     */
    @PostMapping("/reviews/{reviewId}/moderate")
    @Operation(summary = "Hide or show a review")
    @PreAuthorize("@DestinationPolicy.canManage(authentication)")
    public ResponseEntity<ReviewResponse> moderate(@PathVariable UUID reviewId, @RequestParam boolean isHiding,
                                                   @RequestParam(required = false) @Size(max = 500) String reason,
                                                   Authentication authentication) {
        return ResponseEntity.ok(reviewService.moderate(reviewId, isHiding, reason,
                SecurityUtils.extractUserId(authentication)));
    }

    /**
     * Get the reports of reviews, open or settled.
     *
     * @param isOpen whether to get the open ones
     * @param filter the page
     * @return a page of reports
     */
    @GetMapping("/review-reports")
    @Operation(summary = "Get review reports")
    @PreAuthorize("@DestinationPolicy.isStaff(authentication)")
    public ResponseEntity<Paginated<ReviewReportResponse>> reviewReports(
            @RequestParam(defaultValue = "true") boolean isOpen, @Valid @ModelAttribute PaginationFilter filter) {
        return ResponseEntity.ok(reviewService.getReports(isOpen, filter));
    }

    /**
     * Settle every open report of a review by keeping or hiding it.
     *
     * @param reportId the ID of one of its reports
     * @param isHiding whether to hide the review
     * @param authentication the caller
     * @return the report
     */
    @PostMapping("/review-reports/{reportId}/resolve")
    @Operation(summary = "Settle a review's reports")
    @PreAuthorize("@DestinationPolicy.canManage(authentication)")
    public ResponseEntity<ReviewReportResponse> resolve(@PathVariable UUID reportId, @RequestParam boolean isHiding,
                                                        Authentication authentication) {
        return ResponseEntity.ok(reviewService.resolve(reportId, isHiding,
                SecurityUtils.extractUserId(authentication)));
    }

    /**
     * Ask about a place; follow the question until READY.
     *
     * @param placeId the ID of the place
     * @param request the question
     * @param acceptLanguage the Accept-Language header, used without a language
     * @param groupId the group the caller acts for, or null
     * @param authentication the caller
     * @return the question, GENERATING
     */
    @PostMapping("/places/{placeId}/questions")
    @Operation(summary = "Ask about a place")
    @PreAuthorize("@DestinationPolicy.canBrowse(authentication, #groupId)")
    public ResponseEntity<QuestionResponse> ask(
            @PathVariable UUID placeId, @Valid @RequestBody QuestionRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
            @RequestParam(required = false) UUID groupId, Authentication authentication) {
        return new ResponseEntity<>(questionService.ask(placeId, SecurityUtils.extractUserId(authentication),
                request.getQuestion(), request.getLanguage() != null ? request.getLanguage() : acceptLanguage),
                HttpStatus.ACCEPTED);
    }

    /**
     * Get the caller's questions about a place, the newest first.
     *
     * @param placeId the ID of the place
     * @param filter the page
     * @param authentication the caller
     * @return a page of questions
     */
    @GetMapping("/places/{placeId}/questions/mine")
    @Operation(summary = "Get my questions about a place")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Paginated<QuestionResponse>> myQuestions(@PathVariable UUID placeId,
                                                                   @Valid @ModelAttribute PaginationFilter filter,
                                                                   Authentication authentication) {
        return ResponseEntity.ok(questionService.getMine(placeId, SecurityUtils.extractUserId(authentication),
                filter));
    }

    /**
     * Get one of the caller's questions; staff see any.
     *
     * @param questionId the ID of the question
     * @param authentication the caller
     * @return the question
     */
    @GetMapping("/questions/{questionId}")
    @Operation(summary = "Get a question")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<QuestionResponse> question(@PathVariable UUID questionId, Authentication authentication) {
        return ResponseEntity.ok(questionService.get(questionId, SecurityUtils.extractUserId(authentication),
                destinationPolicy.isStaff(authentication)));
    }
}
