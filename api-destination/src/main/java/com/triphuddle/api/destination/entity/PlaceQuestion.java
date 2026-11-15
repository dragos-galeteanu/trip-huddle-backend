package com.triphuddle.api.destination.entity;

import com.triphuddle.amqp.ai.AiCitation;
import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
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
 * A user's question about a place and the AI's answer; private to them.
 */
@Entity
@Table(name = "destination_place_questions")
@Getter
@Setter
@NoArgsConstructor
public class PlaceQuestion extends BaseJpaEntity {

    @Column(name = "place_id", nullable = false)
    private UUID placeId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "language_code", nullable = false, length = 8)
    private String languageCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ContentStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer", columnDefinition = "JSONB")
    private String answer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "citations", nullable = false, columnDefinition = "JSONB")
    private List<AiCitation> citations = new ArrayList<>();

    @Column(name = "model", length = 64)
    private String model;

    @Column(name = "pending_job_id", unique = true)
    private UUID pendingJobId;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;
}
