package com.triphuddle.api.destination.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A day-by-day itinerary the AI wrote for a trip plan; travellers edit it afterwards. A plan keeps its earlier
 * itineraries.
 */
@Entity
@Table(
        name = "destination_itineraries",
        uniqueConstraints = @UniqueConstraint(name = "uk_itinerary_pending_job", columnNames = "pending_job_id")
)
@Getter
@Setter
@NoArgsConstructor
public class Itinerary extends BaseJpaEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private TripPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ItineraryStatus status;

    @Column(name = "language_code", nullable = false, length = 8)
    private String languageCode;

    @Column(name = "requested_by_user_id", nullable = false)
    private UUID requestedByUserId;

    // The job in flight for the whole itinerary; null when none is.
    @Column(name = "pending_job_id")
    private UUID pendingJobId;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "model", length = 64)
    private String model;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;

    @OneToMany(mappedBy = "itinerary", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNumber asc")
    private List<ItineraryDay> days = new ArrayList<>();
}
