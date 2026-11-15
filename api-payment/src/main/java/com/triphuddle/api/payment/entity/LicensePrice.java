package com.triphuddle.api.payment.entity;

import com.triphuddle.amqp.license.BillingInterval;
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

import java.util.UUID;

/**
 * A Stripe Price of a license: one per amount, currency and interval, so subscribers keep the price they started at.
 */
@Entity
@Table(
        name = "payment_license_prices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_license_price_lookup_key", columnNames = "lookup_key"),
                @UniqueConstraint(name = "uk_license_price_stripe_price", columnNames = "stripe_price_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class LicensePrice extends BaseJpaEntity {

    @Column(name = "license_id", nullable = false)
    private UUID licenseId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 16)
    private BillingInterval billingInterval;

    @Column(name = "lookup_key", nullable = false)
    private String lookupKey;

    @Column(name = "stripe_price_id", nullable = false)
    private String stripePriceId;
}
