package com.triphuddle.api.destination.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A place in a country's tree: a region, city, district, area or point of interest, at any depth. Its point and
 * search columns in the database are worked out from its coordinates and names.
 */
@Entity
@Table(name = "destination_places")
@Getter
@Setter
@NoArgsConstructor
@BatchSize(size = 50)
public class Place extends BaseJpaEntity {

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Place parent;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private PlaceKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PlaceStatus status = PlaceStatus.ACTIVE;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 220)
    private String slug;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "wikidata_id", unique = true, length = 32)
    private String wikidataId;

    // OpenStreetMap's type and number, e.g. relation/295480.
    @Column(name = "osm_id", unique = true, length = 40)
    private String osmId;

    @Column(name = "population")
    private Long population;

    @Column(name = "is_featured", nullable = false)
    private boolean isFeatured;

    @Column(name = "merged_into_id")
    private UUID mergedIntoId;

    // The IDs from the top ancestor down to this place, as /id/id/id/, so a subtree is a prefix match.
    @Column(name = "path", nullable = false, columnDefinition = "TEXT")
    private String path = "/";

    @Column(name = "depth", nullable = false)
    private int depth;

    @OneToMany(mappedBy = "place", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private Set<PlaceName> names = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(name = "destination_place_categories",
            joinColumns = @JoinColumn(name = "place_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    @BatchSize(size = 50)
    private Set<Category> categories = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "destination_place_tags", joinColumns = @JoinColumn(name = "place_id"))
    @Column(name = "tag", nullable = false, length = 64)
    @BatchSize(size = 50)
    private Set<String> tags = new HashSet<>();

    /**
     * Check if the place is shown to customers.
     *
     * @return true if it is active and not deleted
     */
    public boolean isListed() {
        return status == PlaceStatus.ACTIVE && deletedAt == null;
    }
}
