package com.triphuddle.api.destination.repository;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.destination.entity.TripPlan;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Data access for {@link TripPlan}.
 */
@Repository
public interface TripPlanRepository extends BaseJpaRepository<TripPlan, UUID> {

    /**
     * Find an owner's plans, the soonest trip first.
     *
     * @param ownerType USER or GROUP
     * @param ownerId the ID of the user or group
     * @param pageable the page
     * @return a page of plans
     */
    @Query("""
            select p from TripPlan p where p.ownerType = :ownerType and p.ownerId = :ownerId and p.deletedAt is null
            order by p.startDate, p.createdAt
            """)
    Page<TripPlan> findForOwner(@Param("ownerType") LicenseTarget ownerType, @Param("ownerId") UUID ownerId,
                                Pageable pageable);
}
