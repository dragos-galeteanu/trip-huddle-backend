package com.triphuddle.api.license.repository;

import com.triphuddle.api.license.entity.Promotion;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link Promotion}.
 */
@Repository
public interface PromotionRepository extends BaseJpaRepository<Promotion, UUID> {

    /**
     * Find a promotion of a license that is not deleted.
     *
     * @param id the ID of the promotion
     * @param licenseId the ID of the license
     * @return the promotion, or empty if it is missing, deleted or of another license
     */
    Optional<Promotion> findByIdAndLicenseIdAndDeletedAtIsNull(UUID id, UUID licenseId);

    /**
     * Find the promotions of a license that are not deleted, latest start first.
     *
     * @param licenseId the ID of the license
     * @param pageable the page to return
     * @return a page of promotions
     */
    Page<Promotion> findByLicenseIdAndDeletedAtIsNullOrderByStartsAtDesc(UUID licenseId, Pageable pageable);

    /**
     * Find the automatic promotions of a license that are not deleted, to check a new run against.
     *
     * @param licenseId the ID of the license
     * @return the promotions without a code
     */
    List<Promotion> findByLicenseIdAndCodeIsNullAndDeletedAtIsNull(UUID licenseId);

    /**
     * Find the promotion of a license with the given code.
     *
     * @param licenseId the ID of the license
     * @param code the code, in upper case
     * @return the promotion, or empty if no promotion that is not deleted has the code
     */
    Optional<Promotion> findByLicenseIdAndCodeAndDeletedAtIsNull(UUID licenseId, String code);

    /**
     * Check if a promotion of a license that is not deleted has the given code.
     *
     * @param licenseId the ID of the license
     * @param code the code, in upper case
     * @return true if such a promotion has the code, false otherwise
     */
    boolean existsByLicenseIdAndCodeAndDeletedAtIsNull(UUID licenseId, String code);

    /**
     * Check if another promotion of a license that is not deleted has the given code.
     *
     * @param licenseId the ID of the license
     * @param code the code, in upper case
     * @param id the ID of the promotion to leave out
     * @return true if another such promotion has the code, false otherwise
     */
    boolean existsByLicenseIdAndCodeAndIdNotAndDeletedAtIsNull(UUID licenseId, String code, UUID id);

    /**
     * Find the automatic promotions running now with redemptions left, for the given licenses;
     * as their runs can't overlap, there is at most one per license.
     *
     * @param licenseIds the IDs of the licenses
     * @param now the current moment
     * @return the running automatic promotions
     */
    @Query("""
            select p from Promotion p
            where p.license.id in :licenseIds
              and p.code is null
              and p.deletedAt is null
              and p.startsAt <= :now
              and (p.endsAt is null or p.endsAt > :now)
              and (p.maxRedemptions is null or p.redemptionCount < p.maxRedemptions)
            """)
    List<Promotion> findRedeemableAutomatic(@Param("licenseIds") Collection<UUID> licenseIds,
                                            @Param("now") Instant now);

    /**
     * Take one redemption of a promotion if it has any left, in one statement so that two
     * subscriptions can't both take the last one.
     *
     * @param id the ID of the promotion
     * @return 1 if a redemption was taken, 0 if none was left
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Promotion p set p.redemptionCount = p.redemptionCount + 1
            where p.id = :id and (p.maxRedemptions is null or p.redemptionCount < p.maxRedemptions)
            """)
    // Not cleared afterwards: a loaded Promotion keeps a stale count, which is never written back as it is not dirty.
    int takeRedemption(@Param("id") UUID id);

    /**
     * Give back a redemption of a promotion, when a subscription that took it never started.
     *
     * @param id the ID of the promotion
     * @return the number of promotions updated
     */
    @Modifying(flushAutomatically = true)
    @Query("update Promotion p set p.redemptionCount = p.redemptionCount - 1 where p.id = :id and p.redemptionCount > 0")
    int releaseRedemption(@Param("id") UUID id);
}
