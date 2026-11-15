package com.triphuddle.api.payment.entity;

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

import java.util.UUID;

/**
 * The Stripe Customer of a user or a group: one each, so a group's card and invoices belong to the group.
 */
@Entity
@Table(name = "payment_customers")
@Getter
@Setter
@NoArgsConstructor
public class StripeCustomer extends BaseJpaEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "subscriber_type", nullable = false, length = 16)
    private LicenseTarget subscriberType;

    @Column(name = "subscriber_id", nullable = false)
    private UUID subscriberId;

    @Column(name = "stripe_customer_id", nullable = false, unique = true)
    private String stripeCustomerId;
}
