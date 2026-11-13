package com.triphuddle.api.user.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * The public profile of a user: biography, pictures and links.
 */
@Entity
@Table(name = "user_profiles")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class UserProfile extends BaseJpaEntity {

    @OneToOne
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_user_profile_user"))
    private User user;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(nullable = false, length = 2)
    private String countryCode;

    @Column
    private String profilePictureUrl;

    @Column
    private String coverPictureUrl;

    @Column
    private String xUrl;

    @Column
    private String facebookUrl;

    @Column
    private String instagramUrl;

    @Column
    private String websiteUrl;
}
