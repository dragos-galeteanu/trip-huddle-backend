package com.triphuddle.api.destination.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One thing to do on an itinerary's day, linked to a place when it is one we know.
 */
@Entity
@Table(name = "destination_itinerary_items")
@Getter
@Setter
@NoArgsConstructor
public class ItineraryItem extends BaseJpaEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "day_id", nullable = false)
    private ItineraryDay day;

    @Column(name = "position", nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_of_day", nullable = false, length = 16)
    private TimeOfDay timeOfDay;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // A place of ours it is about, or null for something else, e.g. a meal or a walk.
    @Column(name = "place_id")
    private UUID placeId;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "tips", columnDefinition = "TEXT")
    private String tips;
}
