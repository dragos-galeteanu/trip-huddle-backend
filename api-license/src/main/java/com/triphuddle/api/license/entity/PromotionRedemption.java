package com.triphuddle.api.license.entity;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A subscription's use of a promotion, kept so once-per-customer promotions can tell who used them.
 */
@Entity
@Table(name = "license_promotion_redemptions")
@Getter
@Setter
@NoArgsConstructor
public class PromotionRedemption extends BaseJpaEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id", nullable = false)
    private Promotion promotion;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscriber_type", nullable = false, length = 16)
    private LicenseTarget subscriberType;

    @Column(name = "subscriber_id", nullable = false)
    private UUID subscriberId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false, unique = true)
    private Subscription subscription;
}
