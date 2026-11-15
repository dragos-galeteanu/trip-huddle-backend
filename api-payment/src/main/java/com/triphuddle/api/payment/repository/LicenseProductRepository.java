package com.triphuddle.api.payment.repository;

import com.triphuddle.api.payment.entity.LicenseProduct;
import com.triphuddle.common.repository.BaseJpaRepository;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link LicenseProduct}.
 */
@Repository
public interface LicenseProductRepository extends BaseJpaRepository<LicenseProduct, UUID> {

    /**
     * Find the copy of a license.
     *
     * @param licenseId the ID of the license in api-license
     * @return the copy, once the license was published
     */
    Optional<LicenseProduct> findByLicenseId(UUID licenseId);

    /**
     * Lock the copy of a license, so its Stripe Product and Prices are changed by one transaction at a time.
     *
     * @param licenseId the ID of the license in api-license
     * @return the locked copy
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from LicenseProduct p where p.licenseId = :licenseId")
    Optional<LicenseProduct> lockByLicenseId(@Param("licenseId") UUID licenseId);

    /**
     * Lock the product most overdue for a sync with Stripe, skipping those another instance holds.
     *
     * @param now the current time
     * @param pageable the batch, of one
     * @return the product, if any is due
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // -2 is SKIP LOCKED, so instances running the job at once split the work instead of waiting.
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select p from LicenseProduct p
            where p.nextSyncAt is not null and p.nextSyncAt <= :now
            order by p.nextSyncAt asc
            """)
    List<LicenseProduct> lockDueForSync(@Param("now") Instant now, Pageable pageable);
}
