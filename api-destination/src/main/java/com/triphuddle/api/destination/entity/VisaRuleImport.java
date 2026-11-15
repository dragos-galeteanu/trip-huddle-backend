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
 * One load of the visa rules from the passport-index dataset.
 */
@Entity
// At most one import QUEUED or RUNNING: a partial index, which JPA can't declare.
@Table(name = "destination_visa_rule_imports")
@Getter
@Setter
@NoArgsConstructor
public class VisaRuleImport extends BaseJpaEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ImportStatus status;

    @Column(name = "source_url", nullable = false, columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "requested_by_user_id")
    private UUID requestedByUserId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "rule_count", nullable = false)
    private int ruleCount;

    // Rows with a value this service doesn't know, such as the dataset's old "covid ban".
    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "removed_count", nullable = false)
    private int removedCount;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;
}
