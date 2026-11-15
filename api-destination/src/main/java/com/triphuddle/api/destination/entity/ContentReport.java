package com.triphuddle.api.destination.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's report that a piece of AI content is wrong or unsuitable, waiting for staff.
 */
@Entity
@Table(name = "destination_content_reports")
@Getter
@Setter
@NoArgsConstructor
public class ContentReport extends BaseJpaEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    private PlaceContent content;

    @Column(name = "reported_by_user_id", nullable = false)
    private UUID reportedByUserId;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    // KEPT or REGENERATED once staff dealt with it; null while open.
    @Column(name = "resolution", length = 16)
    private String resolution;

    @Column(name = "resolved_by_user_id")
    private UUID resolvedByUserId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
