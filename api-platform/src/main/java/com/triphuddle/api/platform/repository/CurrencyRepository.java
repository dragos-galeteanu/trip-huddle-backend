package com.triphuddle.api.platform.repository;

import com.triphuddle.api.platform.entity.Currency;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Data access for {@link Currency}.
 */
@Repository
public interface CurrencyRepository extends BaseJpaRepository<Currency, UUID> {

    /**
     * Check if a currency has the given code.
     *
     * @param code the code to look for
     * @return true if a currency has the code, false otherwise
     */
    boolean existsByCode(String code);

    /**
     * Check if another currency has the given code.
     *
     * @param code the code to look for
     * @param id the ID of the currency to leave out
     * @return true if another currency has the code, false otherwise
     */
    boolean existsByCodeAndIdNot(String code, UUID id);
}
