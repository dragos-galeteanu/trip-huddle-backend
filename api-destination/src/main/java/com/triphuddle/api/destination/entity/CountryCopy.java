package com.triphuddle.api.destination.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A local copy of a country from api-platform's events; places can only be added under an active one.
 * Its disabled and deleted timestamps follow the country's.
 */
@Entity
@Table(name = "destination_countries")
@Getter
@Setter
@NoArgsConstructor
public class CountryCopy extends BaseJpaEntity {

    @Column(name = "country_id", nullable = false, unique = true)
    private UUID countryId;

    @Column(name = "alpha2", nullable = false, length = 2)
    private String alpha2;
}
