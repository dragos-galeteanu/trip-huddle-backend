package com.triphuddle.api.destination.entity;

import com.triphuddle.amqp.user.UserTravelDocumentsUpdatedEvent.ResidencePermit;
import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Copy of a user's nationalities and residence permits from api-user; a deleted user's row stays, emptied, so a
 * late update can't bring them back.
 */
@Entity
@Table(
        name = "destination_traveller_documents",
        uniqueConstraints = @UniqueConstraint(name = "uk_traveller_documents_user", columnNames = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
public class TravellerDocuments extends BaseJpaEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "nationalities", nullable = false, columnDefinition = "JSONB")
    private List<String> nationalities = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "residence_permits", nullable = false, columnDefinition = "JSONB")
    private List<ResidencePermit> residencePermits = new ArrayList<>();

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;
}
