package com.triphuddle.api.license.repository;

import com.triphuddle.api.license.entity.License;
import com.triphuddle.api.license.entity.LicenseStatus;
import com.triphuddle.api.license.entity.LicenseTarget;
import com.triphuddle.common.repository.BaseJpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link License}.
 */
@Repository
public interface LicenseRepository extends BaseJpaRepository<License, UUID> {

    /**
     * Check if a license that is not deleted has the given code.
     *
     * @param code the code to look for
     * @return true if such a license has the code, false otherwise
     */
    boolean existsByCodeAndDeletedAtIsNull(String code);

    /**
     * Check if another license that is not deleted has the given code.
     *
     * @param code the code to look for
     * @param id the ID of the license to leave out
     * @return true if another such license has the code, false otherwise
     */
    boolean existsByCodeAndIdNotAndDeletedAtIsNull(String code, UUID id);

    /**
     * Find the licenses that are not deleted, cheapest first and then by name.
     *
     * @param status the status to keep, or null for all
     * @param target the target to keep, or null for all
     * @param pageable the page to return
     * @return a page of the matching licenses
     */
    @Query(value = """
            select l from License l
            where l.deletedAt is null
              and (:status is null or l.status = :status)
              and (:target is null or l.target = :target)
            order by l.priceMinor asc, l.name asc
            """,
            countQuery = """
            select count(l) from License l
            where l.deletedAt is null
              and (:status is null or l.status = :status)
              and (:target is null or l.target = :target)
            """)
    Page<License> findCatalog(@Param("status") LicenseStatus status,
                              @Param("target") LicenseTarget target,
                              Pageable pageable);

    /**
     * Find a license that is not deleted and lock it until the transaction ends, so that changes
     * to its promotions are checked one at a time.
     *
     * @param id the ID of the license
     * @return the license, or empty if it is missing or deleted
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from License l where l.id = :id and l.deletedAt is null")
    Optional<License> lockById(@Param("id") UUID id);
}
