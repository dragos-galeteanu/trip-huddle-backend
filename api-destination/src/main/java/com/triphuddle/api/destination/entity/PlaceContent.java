package com.triphuddle.api.destination.entity;

import com.triphuddle.amqp.ai.AiCitation;
import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One section of a place's AI content in one language, shared by everyone who reads it. While a new version is
 * generated, the last ready one is still served.
 */
@Entity
@Table(
        name = "destination_place_contents",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_place_content_place_section_language_code",
                        columnNames = {"place_id", "section", "language_code"}),
                @UniqueConstraint(name = "uk_place_content_pending_job", columnNames = "pending_job_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class PlaceContent extends BaseJpaEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Enumerated(EnumType.STRING)
    @Column(name = "section", nullable = false, length = 16)
    private ContentSection section;

    // ISO 639-1.
    @Column(name = "language_code", nullable = false, length = 8)
    private String languageCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ContentStatus status;

    // The section's JSON, as its schema shapes it; null until the first version is ready.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", columnDefinition = "JSONB")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "citations", nullable = false, columnDefinition = "JSONB")
    private List<AiCitation> citations = new ArrayList<>();

    @Column(name = "model", length = 64)
    private String model;

    @Column(name = "generated_at")
    private Instant generatedAt;

    // When it is due for a refresh.
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_viewed_at")
    private Instant lastViewedAt;

    // The job in flight, matched against the worker's answer; null when none is.
    @Column(name = "pending_job_id")
    private UUID pendingJobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "pending_priority", length = 16)
    private GenerationPriority pendingPriority;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    /**
     * Check if a version is ready to show, possibly being refreshed.
     *
     * @return true once one was generated
     */
    public boolean hasContent() {
        return content != null;
    }
}
