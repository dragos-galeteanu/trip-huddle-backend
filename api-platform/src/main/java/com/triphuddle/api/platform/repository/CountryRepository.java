package com.triphuddle.api.platform.repository;

import com.triphuddle.api.platform.entity.Country;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Data access for {@link Country}.
 */
@Repository
public interface CountryRepository extends BaseJpaRepository<Country, UUID> {

    /**
     * Check if a country has the given alpha-2 code.
     *
     * @param alpha2 the alpha-2 code to look for
     * @return true if a country has it, false otherwise
     */
    boolean existsByAlpha2(String alpha2);

    /**
     * Check if another country has the given alpha-2 code.
     *
     * @param alpha2 the alpha-2 code to look for
     * @param id the ID of the country to leave out
     * @return true if another country has it, false otherwise
     */
    boolean existsByAlpha2AndIdNot(String alpha2, UUID id);

    /**
     * Check if a country has the given alpha-3 code.
     *
     * @param alpha3 the alpha-3 code to look for
     * @return true if a country has it, false otherwise
     */
    boolean existsByAlpha3(String alpha3);

    /**
     * Check if another country has the given alpha-3 code.
     *
     * @param alpha3 the alpha-3 code to look for
     * @param id the ID of the country to leave out
     * @return true if another country has it, false otherwise
     */
    boolean existsByAlpha3AndIdNot(String alpha3, UUID id);

    /**
     * Check if a country has the given numeric code.
     *
     * @param numericCode the numeric code to look for
     * @return true if a country has it, false otherwise
     */
    boolean existsByNumericCode(String numericCode);

    /**
     * Check if another country has the given numeric code.
     *
     * @param numericCode the numeric code to look for
     * @param id the ID of the country to leave out
     * @return true if another country has it, false otherwise
     */
    boolean existsByNumericCodeAndIdNot(String numericCode, UUID id);
}
