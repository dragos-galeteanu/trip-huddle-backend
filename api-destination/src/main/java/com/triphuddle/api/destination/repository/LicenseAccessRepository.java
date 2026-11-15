package com.triphuddle.api.destination.repository;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.destination.entity.LicenseAccess;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link LicenseAccess}.
 */
@Repository
public interface LicenseAccessRepository extends BaseJpaRepository<LicenseAccess, UUID> {

    /**
     * Find the copy of a subscription.
     *
     * @param subscriptionId the ID of the subscription in api-license
     * @return the copy
     */
    Optional<LicenseAccess> findBySubscriptionId(UUID subscriptionId);

    /**
     * Find the copies of every subscription to a license, for an entitlement change.
     *
     * @param licenseId the ID of the license
     * @return the copies
     */
    List<LicenseAccess> findByLicenseId(UUID licenseId);

    /**
     * Find a subscriber's subscriptions that still give access.
     *
     * @param subscriberType USER or GROUP
     * @param subscriberId the ID of the user or group
     * @param after the moment the period must end after; now less the grace
     * @return the subscriptions
     */
    @Query("""
            select a from LicenseAccess a
            where a.subscriberType = :subscriberType and a.subscriberId = :subscriberId
              and a.endedAt is null and a.currentPeriodEnd > :after
            """)
    List<LicenseAccess> findCurrent(@Param("subscriberType") LicenseTarget subscriberType,
                                    @Param("subscriberId") UUID subscriberId, @Param("after") Instant after);
}
