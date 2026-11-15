package com.triphuddle.api.license.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One thing a license grants, by key: a limit such as {@code MAX_GROUPS=10}, or a flag when it has no value.
 */
@Entity
@Table(name = "license_entitlements",
        uniqueConstraints = @UniqueConstraint(columnNames = {"license_id", "entitlement_key"}))
@Getter
@Setter
@NoArgsConstructor
public class LicenseEntitlement extends BaseJpaEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "license_id", nullable = false)
    private License license;

    @Column(name = "entitlement_key", nullable = false, length = 64)
    private String entitlementKey;

    @Column(name = "entitlement_value")
    private String entitlementValue;
}
