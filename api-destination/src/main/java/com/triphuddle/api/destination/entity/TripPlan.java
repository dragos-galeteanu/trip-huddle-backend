package com.triphuddle.api.destination.entity;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A trip a user or a group is planning: where, when, who, and how; itineraries are written for it.
 */
@Entity
@Table(name = "destination_trip_plans")
@Getter
@Setter
@NoArgsConstructor
public class TripPlan extends BaseJpaEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 16)
    private LicenseTarget ownerType;

    // The user, or the group whose members share it.
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "adults", nullable = false)
    private int adults;

    @Column(name = "children", nullable = false)
    private int children;

    @Enumerated(EnumType.STRING)
    @Column(name = "budget_level", nullable = false, length = 16)
    private BudgetLevel budgetLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "pace", nullable = false, length = 16)
    private TripPace pace;

    @ElementCollection
    @CollectionTable(name = "destination_trip_plan_interests", joinColumns = @JoinColumn(name = "plan_id"))
    @Column(name = "interest", nullable = false, length = 32)
    private Set<String> interests = new HashSet<>();

    // The places the trip goes to, in order.
    @ElementCollection
    @CollectionTable(name = "destination_trip_plan_places", joinColumns = @JoinColumn(name = "plan_id"))
    @OrderColumn(name = "position")
    @Column(name = "place_id", nullable = false)
    private List<UUID> placeIds = new ArrayList<>();

    // Places the travellers want in the itinerary whatever happens.
    @ElementCollection
    @CollectionTable(name = "destination_trip_plan_must_sees", joinColumns = @JoinColumn(name = "plan_id"))
    @Column(name = "place_id", nullable = false)
    private Set<UUID> mustSeePlaceIds = new HashSet<>();

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ISO 639-1, the language itineraries are written in.
    @Column(name = "language_code", nullable = false, length = 8)
    private String languageCode;

    /**
     * How many days the trip has, both ends included.
     *
     * @return the number of days
     */
    public int dayCount() {
        return (int) (endDate.toEpochDay() - startDate.toEpochDay()) + 1;
    }
}
