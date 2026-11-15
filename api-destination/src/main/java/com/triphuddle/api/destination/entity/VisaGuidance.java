package com.triphuddle.api.destination.entity;

import com.triphuddle.amqp.ai.AiCitation;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The AI's explanation of how a passport holder, with given residence permits, enters a country; shared by every
 * traveller in the same case, so it holds nothing about any one of them.
 */
@Entity
@Table(
        name = "destination_visa_guidance",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_visa_guidance_guidance_key", columnNames = "guidance_key"),
                @UniqueConstraint(name = "uk_visa_guidance_pending_job", columnNames = "pending_job_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class VisaGuidance extends BaseJpaEntity {

    // Destination, passport, requirement, permit countries and language, joined by "|".
    @Column(name = "guidance_key", nullable = false, length = 120)
    private String guidanceKey;

    @Column(name = "destination_code", nullable = false, length = 2)
    private String destinationCode;

    @Column(name = "passport_code", nullable = false, length = 2)
    private String passportCode;

    // Sorted and comma-separated; empty for none.
    @Column(name = "permit_codes", nullable = false, length = 64)
    private String permitCodes = "";

    // Null when the dataset has no rule for the pair.
    @Enumerated(EnumType.STRING)
    @Column(name = "requirement", length = 24)
    private VisaRequirement requirement;

    @Column(name = "allowed_days")
    private Integer allowedDays;

    @Column(name = "language_code", nullable = false, length = 8)
    private String languageCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ContentStatus status;

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

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "pending_job_id")
    private UUID pendingJobId;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
}
