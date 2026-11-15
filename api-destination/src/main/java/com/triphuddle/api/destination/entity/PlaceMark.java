package com.triphuddle.api.destination.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A place a user saved as a favourite or marked as visited; private to them.
 */
@Entity
@Table(
        name = "destination_place_marks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_place_mark_user_place_kind", columnNames = {"user_id", "place_id", "kind"})
)
@Getter
@Setter
@NoArgsConstructor
public class PlaceMark extends BaseJpaEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "place_id", nullable = false)
    private UUID placeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private PlaceMarkKind kind;

    // Only for VISITED, and only if the user gave it.
    @Column(name = "visited_on")
    private LocalDate visitedOn;
}
