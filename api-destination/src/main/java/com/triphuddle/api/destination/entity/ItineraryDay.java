package com.triphuddle.api.destination.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One day of an itinerary; it can be written again on its own.
 */
@Entity
@Table(name = "destination_itinerary_days")
@Getter
@Setter
@NoArgsConstructor
public class ItineraryDay extends BaseJpaEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "itinerary_id", nullable = false)
    private Itinerary itinerary;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    // The job rewriting this day alone; null when none is.
    @Column(name = "pending_job_id", unique = true)
    private UUID pendingJobId;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position asc")
    private List<ItineraryItem> items = new ArrayList<>();
}
