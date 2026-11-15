package com.triphuddle.api.payment.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A local copy of a user's contact from api-user's events, for Stripe customers and invoices.
 * Its disabled and deleted timestamps follow the user's.
 */
@Entity
@Table(
        name = "payment_user_contacts",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_contact_user", columnNames = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
public class UserContact extends BaseJpaEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // Null until the first contact event, when a disabled or deleted event came first.
    @Column(name = "email")
    private String email;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "is_email_verified", nullable = false)
    private boolean isEmailVerified;

    @Column(name = "contact_changed_at")
    private Instant contactChangedAt;
}
