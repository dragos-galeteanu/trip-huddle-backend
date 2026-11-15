package com.triphuddle.api.destination.entity;

import com.triphuddle.amqp.license.LicenseTarget;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A local copy of an active license subscription and its entitlements, from api-license's events; it decides who
 * may use the service and how much.
 */
@Entity
@Table(
        name = "destination_license_subscriptions",
        uniqueConstraints = @UniqueConstraint(name = "uk_license_access_subscription", columnNames = "subscription_id")
)
@Getter
@Setter
@NoArgsConstructor
public class LicenseAccess extends BaseJpaEntity {

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "license_id", nullable = false)
    private UUID licenseId;

    @Column(name = "license_code", length = 64)
    private String licenseCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscriber_type", nullable = false, length = 16)
    private LicenseTarget subscriberType;

    @Column(name = "subscriber_id", nullable = false)
    private UUID subscriberId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "entitlements", nullable = false, columnDefinition = "JSONB")
    private Map<String, String> entitlements = new HashMap<>();

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    // Set by its end event; an activation arriving later is then ignored.
    @Column(name = "ended_at")
    private Instant endedAt;
}
