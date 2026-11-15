package com.triphuddle.api.user.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import com.triphuddle.common.security.UserRole;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A platform user, linked to their Firebase account by {@code firebaseUid}.
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_firebase_uid", columnNames = "firebase_uid"),
                @UniqueConstraint(name = "uk_user_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_user_phone_number", columnNames = "phone_number")
        }
)
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class User extends BaseJpaEntity {

    @Column(nullable = false)
    private String firebaseUid;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column
    private UserRole firebaseSyncedRole;

    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column
    private UUID emailVerificationToken;

    @OneToOne(mappedBy = "user")
    private UserProfile profile;
}
