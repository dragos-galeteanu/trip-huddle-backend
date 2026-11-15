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

/**
 * A freely licensed photo of a place, with what its licence asks to be shown next to it.
 */
@Entity
@Table(name = "destination_place_photos")
@Getter
@Setter
@NoArgsConstructor
public class PlacePhoto extends BaseJpaEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private PhotoSource source;

    // The file on Commons, e.g. Porto_Ribeira.jpg; one photo per file and place.
    @Column(name = "source_file_name", nullable = false, length = 255)
    private String sourceFileName;

    // The image on Commons at the imported width; shown when no copy is stored.
    @Column(name = "source_url", nullable = false, columnDefinition = "TEXT")
    private String sourceUrl;

    // The copy in the bucket, or null when none is configured.
    @Column(name = "storage_path", length = 255)
    private String storagePath;

    @Column(name = "width", nullable = false)
    private int width;

    @Column(name = "height", nullable = false)
    private int height;

    @Column(name = "mime_type", length = 64)
    private String mimeType;

    @Column(name = "author", columnDefinition = "TEXT")
    private String author;

    @Column(name = "license_name", nullable = false, length = 64)
    private String licenseName;

    @Column(name = "license_url", columnDefinition = "TEXT")
    private String licenseUrl;

    @Column(name = "source_page_url", columnDefinition = "TEXT")
    private String sourcePageUrl;

    @Column(name = "is_attribution_required", nullable = false)
    private boolean isAttributionRequired;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;
}
