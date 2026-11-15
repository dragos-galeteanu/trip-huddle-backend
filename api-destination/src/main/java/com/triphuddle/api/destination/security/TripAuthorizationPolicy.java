package com.triphuddle.api.destination.security;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.destination.dto.TripPlanRequest;
import com.triphuddle.api.destination.entity.Itinerary;
import com.triphuddle.api.destination.entity.TripPlan;
import com.triphuddle.api.destination.repository.GroupMemberRepository;
import com.triphuddle.api.destination.repository.ItineraryRepository;
import com.triphuddle.api.destination.repository.TripPlanRepository;
import com.triphuddle.api.destination.service.AccessService;
import com.triphuddle.common.security.BaseAuthorizationPolicy;
import com.triphuddle.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * A personal plan is its user's, a group plan its members'; either needs the license that opens the service, the
 * user's own or the group's. Staff read every plan. The creator or a group admin deletes a plan.
 */
@Component("TripPolicy")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripAuthorizationPolicy implements BaseAuthorizationPolicy<TripPlanRequest, Void, UUID> {

    private static final String ADMIN_ROLE = "ADMIN";

    private final TripPlanRepository planRepository;
    private final ItineraryRepository itineraryRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final AccessService accessService;

    @Override
    public boolean canCreate(Authentication authentication, TripPlanRequest request) {
        return isAuthenticated(authentication) && request != null
                && accessService.hasAccess(SecurityUtils.extractUserId(authentication), request.getGroupId());
    }

    @Override
    public boolean canGetById(Authentication authentication, UUID planId) {
        return isAdminOrSupportOrInspector(authentication) || canUse(authentication, planId);
    }

    /**
     * Check if the user may change a plan and have itineraries written for it.
     *
     * @param authentication the authentication object
     * @param planId the ID of the plan
     * @return true for its user, or a member of its group, with the license
     */
    public boolean canUse(Authentication authentication, UUID planId) {
        if (!isAuthenticated(authentication) || planId == null) {
            return false;
        }
        UUID userId = SecurityUtils.extractUserId(authentication);
        return planRepository.findByIdAndDeletedAtIsNull(planId).map(plan -> isTraveller(plan, userId)).orElse(false);
    }

    /**
     * Check if the user may change an itinerary, through its plan.
     *
     * @param authentication the authentication object
     * @param itineraryId the ID of the itinerary
     * @return true if they may use its plan
     */
    public boolean canUseItinerary(Authentication authentication, UUID itineraryId) {
        Optional<UUID> planId = itineraryRepository.findByIdAndDeletedAtIsNull(itineraryId)
                .map(Itinerary::getPlan).map(TripPlan::getId);
        return planId.isPresent() && canUse(authentication, planId.get());
    }

    /**
     * Check if the user may read an itinerary, through its plan.
     *
     * @param authentication the authentication object
     * @param itineraryId the ID of the itinerary
     * @return true for staff, or if they may use its plan
     */
    public boolean canReadItinerary(Authentication authentication, UUID itineraryId) {
        return isAdminOrSupportOrInspector(authentication) || canUseItinerary(authentication, itineraryId);
    }

    /**
     * Check if the user may list a group's plans.
     *
     * @param authentication the authentication object
     * @param groupId the ID of the group
     * @return true for staff, or a member with the group's license
     */
    public boolean canGetForGroup(Authentication authentication, UUID groupId) {
        return isAdminOrSupportOrInspector(authentication) || (isAuthenticated(authentication)
                && accessService.hasAccess(SecurityUtils.extractUserId(authentication), groupId));
    }

    /**
     * Check if the user may list their own plans.
     *
     * @param authentication the authentication object
     * @return true with their own license
     */
    public boolean canGetMine(Authentication authentication) {
        return isAuthenticated(authentication)
                && accessService.hasAccess(SecurityUtils.extractUserId(authentication), null);
    }

    @Override
    public boolean canDelete(Authentication authentication, UUID planId) {
        if (!canUse(authentication, planId)) {
            return false;
        }
        UUID userId = SecurityUtils.extractUserId(authentication);
        TripPlan plan = planRepository.findByIdAndDeletedAtIsNull(planId).orElseThrow();
        return plan.getCreatedByUserId().equals(userId) || (plan.getOwnerType() == LicenseTarget.GROUP
                && groupMemberRepository.existsByGroupIdAndUserIdAndRole(plan.getOwnerId(), userId, ADMIN_ROLE));
    }

    private boolean isTraveller(TripPlan plan, UUID userId) {
        if (plan.getOwnerType() == LicenseTarget.USER) {
            return plan.getOwnerId().equals(userId) && accessService.hasAccess(userId, null);
        }
        return accessService.hasAccess(userId, plan.getOwnerId());
    }

    @Override
    public boolean canGetAll(Authentication authentication) {
        return isAdminOrSupportOrInspector(authentication);
    }

    @Override
    public boolean canUpdate(Authentication authentication, Void request) {
        return false;
    }

    @Override
    public boolean canDisable(Authentication authentication) {
        return false;
    }
}
