package com.triphuddle.api.payment.entity;

import com.triphuddle.amqp.license.BillingInterval;
import com.triphuddle.amqp.license.LicenseTarget;
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
 * A license as api-license last reported it, and the state of its Stripe Product.
 */
@Entity
@Table(name = "payment_license_products")
@Getter
@Setter
@NoArgsConstructor
public class LicenseProduct extends BaseJpaEntity {

    @Column(name = "license_id", nullable = false, unique = true)
    private UUID licenseId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "target", nullable = false, length = 16)
    private LicenseTarget target;

    /**
     * PUBLISHED or ARCHIVED, as in api-license.
     */
    @Column(name = "license_status", nullable = false, length = 16)
    private String licenseStatus;

    @Column(name = "price_minor", nullable = false)
    private long priceMinor;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 16)
    private BillingInterval billingInterval;

    @Column(name = "catalog_changed_at", nullable = false)
    private Instant catalogChangedAt;

    @Column(name = "stripe_product_id", nullable = false, unique = true)
    private String stripeProductId;

    // Null while Stripe matches what is stored here.
    @Column(name = "next_sync_at")
    private Instant nextSyncAt;

    @Column(name = "sync_attempts", nullable = false)
    private int syncAttempts;

    @Column(name = "last_sync_error", columnDefinition = "TEXT")
    private String lastSyncError;

    @Column(name = "synced_at")
    private Instant syncedAt;

    /**
     * Check if the license takes new subscriptions.
     *
     * @return true if it is published
     */
    public boolean isPublished() {
        return "PUBLISHED".equals(licenseStatus);
    }
}
