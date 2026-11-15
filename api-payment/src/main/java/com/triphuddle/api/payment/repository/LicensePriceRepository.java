package com.triphuddle.api.payment.repository;

import com.triphuddle.api.payment.entity.LicensePrice;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link LicensePrice}.
 */
@Repository
public interface LicensePriceRepository extends BaseJpaRepository<LicensePrice, UUID> {

    /**
     * Find a price by its lookup key.
     *
     * @param lookupKey the key of the license, amount, currency and interval
     * @return the price, if it was created
     */
    Optional<LicensePrice> findByLookupKey(String lookupKey);
}
