package com.triphuddle.api.destination.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A staff request to import a country's regions, cities and places to visit from Wikidata, with their photos.
 */
@Entity
@Table(name = "destination_import_jobs")
@Getter
@Setter
@NoArgsConstructor
public class ImportJob extends BaseJpaEntity {

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ImportStatus status = ImportStatus.QUEUED;

    @Column(name = "requested_by_user_id", nullable = false)
    private UUID requestedByUserId;

    @Column(name = "min_city_population", nullable = false)
    private long minCityPopulation;

    @Column(name = "min_poi_sitelinks", nullable = false)
    private int minPoiSitelinks;

    @Column(name = "is_including_pois", nullable = false)
    private boolean isIncludingPois;

    @Column(name = "is_including_photos", nullable = false)
    private boolean isIncludingPhotos;

    @Column(name = "country_wikidata_id", length = 32)
    private String countryWikidataId;

    // REGIONS, CITIES, PLACES or PHOTOS while running.
    @Column(name = "stage", length = 16)
    private String stage;

    @Column(name = "places_created", nullable = false)
    private int placesCreated;

    @Column(name = "places_updated", nullable = false)
    private int placesUpdated;

    @Column(name = "photos_added", nullable = false)
    private int photosAdded;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;

    @Column(name = "started_at")
    private Instant startedAt;

    // Renewed while it runs; a RUNNING job that stops renewing it was cut off, and is taken up again.
    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
