package com.triphuddle.api.destination.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A name a place is known by in a language, or an alias such as an old or a merged place's name; search looks
 * through all of them.
 */
@Entity
@Table(name = "destination_place_names")
@Getter
@Setter
@NoArgsConstructor
public class PlaceName extends BaseJpaEntity {

    /** The language code for a name of no particular language. */
    public static final String ANY_LANGUAGE = "und";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    // An ISO 639-1 code, or "und".
    @Column(name = "language_code", nullable = false, length = 8)
    private String languageCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "is_alias", nullable = false)
    private boolean isAlias;
}
