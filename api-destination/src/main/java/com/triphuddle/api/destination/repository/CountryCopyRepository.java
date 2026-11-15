package com.triphuddle.api.destination.repository;

import com.triphuddle.api.destination.entity.CountryCopy;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link CountryCopy}.
 */
@Repository
public interface CountryCopyRepository extends BaseJpaRepository<CountryCopy, UUID> {

    /**
     * Find the copy of a country.
     *
     * @param countryId the ID of the country in api-platform
     * @return the copy
     */
    Optional<CountryCopy> findByCountryId(UUID countryId);

    /**
     * Check if a country is active.
     *
     * @param alpha2 the ISO 3166 alpha-2 code
     * @return true if a copy of it is neither disabled nor deleted
     */
    boolean existsByAlpha2AndDisabledAtIsNullAndDeletedAtIsNull(String alpha2);
}
