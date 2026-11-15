package com.triphuddle.api.user.entity;

import com.triphuddle.amqp.user.ResidencePermitKind;
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
 * A nationality or residence permit of a user, used for visa guidance; no document numbers are kept.
 */
@Entity
@Table(
        name = "user_travel_documents",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_travel_document_user_type_country_code",
                columnNames = {"user_id", "type", "country_code"})
)
@Getter
@Setter
@NoArgsConstructor
public class UserTravelDocument extends BaseJpaEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 24)
    private TravelDocumentType type;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    // Only for residence permits.
    @Enumerated(EnumType.STRING)
    @Column(name = "permit_kind", length = 16)
    private ResidencePermitKind permitKind;

    @Column(name = "valid_until")
    private LocalDate validUntil;
}
