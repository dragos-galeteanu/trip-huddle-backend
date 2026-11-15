package com.triphuddle.api.license.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A license offered by the platform: a recurring price and the entitlements it grants.
 */
// Loads the licenses or promotions of a page of subscriptions in a few queries rather than one each.
@BatchSize(size = 50)
@Entity
@Table(name = "licenses")
@Getter
@Setter
@NoArgsConstructor
public class License extends BaseJpaEntity {

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "target", nullable = false, length = 16)
    private LicenseTarget target;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private LicenseStatus status = LicenseStatus.DRAFT;

    // In minor units of the platform currency, so 999 is 9.99 EUR.
    @Column(name = "price_minor", nullable = false)
    private long priceMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 16)
    private BillingInterval billingInterval;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @OneToMany(mappedBy = "license", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("entitlementKey asc")
    // Loads the entitlements of a whole catalog page in a few queries rather than one per license.
    @BatchSize(size = 50)
    private List<LicenseEntitlement> entitlements = new ArrayList<>();
}
