package com.triphuddle.api.destination.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A user's review of a place: one each, which they may change or delete.
 */
@Entity
// One review per user and place among those not deleted: a partial index, which JPA can't declare.
@Table(name = "destination_place_reviews")
@Getter
@Setter
@NoArgsConstructor
public class PlaceReview extends BaseJpaEntity {

    @Column(name = "place_id", nullable = false)
    private UUID placeId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "title", length = 120)
    private String title;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "visited_on")
    private LocalDate visitedOn;

    @Column(name = "language_code", nullable = false, length = 8)
    private String languageCode;

    @Column(name = "is_hidden", nullable = false)
    private boolean isHidden;

    @Column(name = "hidden_reason", length = 500)
    private String hiddenReason;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    // Null when hidden by reports rather than by staff.
    @Column(name = "hidden_by_user_id")
    private UUID hiddenByUserId;

    @Column(name = "open_report_count", nullable = false)
    private int openReportCount;
}
