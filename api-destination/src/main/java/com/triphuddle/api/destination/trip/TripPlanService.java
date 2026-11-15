package com.triphuddle.api.destination.trip;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.destination.config.TripProperties;
import com.triphuddle.api.destination.dto.PlaceRef;
import com.triphuddle.api.destination.dto.TripPlanRequest;
import com.triphuddle.api.destination.dto.TripPlanResponse;
import com.triphuddle.api.destination.entity.Place;
import com.triphuddle.api.destination.entity.TripPlan;
import com.triphuddle.api.destination.mapper.PlaceMapper;
import com.triphuddle.api.destination.repository.PlaceRepository;
import com.triphuddle.api.destination.repository.TripPlanRepository;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Trip plans of users and of groups, whose members share them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TripPlanService {

    private final TripPlanRepository planRepository;
    private final PlaceRepository placeRepository;
    private final PlaceMapper placeMapper;
    private final TripProperties properties;

    /**
     * Create a plan for the caller, or for a group they belong to.
     *
     * @param request the plan
     * @param userId the caller
     * @return the plan
     */
    public TripPlanResponse create(TripPlanRequest request, UUID userId) {
        TripPlan plan = new TripPlan();
        plan.setOwnerType(request.getGroupId() == null ? LicenseTarget.USER : LicenseTarget.GROUP);
        plan.setOwnerId(request.getGroupId() == null ? userId : request.getGroupId());
        plan.setCreatedByUserId(userId);
        apply(plan, request);
        TripPlan saved = planRepository.save(plan);
        log.info("Trip plan created [id={}, ownerType={}, ownerId={}]", saved.getId(), saved.getOwnerType(),
                saved.getOwnerId());
        return toResponse(saved);
    }

    /**
     * Change a plan; its itineraries stay as they were written.
     *
     * @param planId the ID of the plan
     * @param request the new details
     * @return the plan
     */
    public TripPlanResponse update(UUID planId, TripPlanRequest request) {
        TripPlan plan = find(planId);
        apply(plan, request);
        return toResponse(plan);
    }

    /**
     * Delete a plan.
     *
     * @param planId the ID of the plan
     */
    public void delete(UUID planId) {
        find(planId).setDeletedAt(Instant.now());
        log.info("Trip plan deleted [id={}]", planId);
    }

    /**
     * Get a plan.
     *
     * @param planId the ID of the plan
     * @return the plan
     */
    @Transactional(readOnly = true)
    public TripPlanResponse get(UUID planId) {
        return toResponse(find(planId));
    }

    /**
     * Get an owner's plans, the soonest first.
     *
     * @param ownerType USER or GROUP
     * @param ownerId the user or group
     * @param filter the page
     * @return a page of plans
     */
    @Transactional(readOnly = true)
    public Paginated<TripPlanResponse> getForOwner(LicenseTarget ownerType, UUID ownerId, PaginationFilter filter) {
        return Paginated.of(planRepository.findForOwner(ownerType, ownerId, filter.toPageable()),
                plans -> plans.stream().map(this::toResponse).toList());
    }

    /**
     * Get a plan that is not deleted.
     *
     * @param planId the ID of the plan
     * @return the plan
     */
    @Transactional(readOnly = true)
    public TripPlan find(UUID planId) {
        return planRepository.findByIdAndDeletedAtIsNull(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip plan with id=%s not found".formatted(planId)));
    }

    private void apply(TripPlan plan, TripPlanRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalStateException("The trip can't end before it starts");
        }
        long days = request.getEndDate().toEpochDay() - request.getStartDate().toEpochDay() + 1;
        if (days > properties.maxTripDays()) {
            throw new IllegalStateException("A trip can last at most %d days".formatted(properties.maxTripDays()));
        }
        List<UUID> placeIds = new ArrayList<>(new LinkedHashSet<>(request.getPlaceIds()));
        if (placeIds.size() > properties.maxPlaces()) {
            throw new IllegalStateException("A trip can go to at most %d places".formatted(properties.maxPlaces()));
        }
        requireListed(placeIds);
        requireListed(request.getMustSeePlaceIds());
        plan.setTitle(request.getTitle().strip());
        plan.setStartDate(request.getStartDate());
        plan.setEndDate(request.getEndDate());
        plan.setAdults(request.getAdults());
        plan.setChildren(request.getChildren());
        plan.setBudgetLevel(request.getBudgetLevel());
        plan.setPace(request.getPace());
        plan.getInterests().clear();
        plan.getInterests().addAll(request.getInterests());
        plan.getPlaceIds().clear();
        plan.getPlaceIds().addAll(placeIds);
        plan.getMustSeePlaceIds().clear();
        plan.getMustSeePlaceIds().addAll(request.getMustSeePlaceIds());
        plan.setNotes(request.getNotes() == null || request.getNotes().isBlank() ? null : request.getNotes().strip());
        plan.setLanguageCode(request.getLanguage() == null ? "en" : request.getLanguage().toLowerCase(Locale.ROOT));
    }

    private void requireListed(Collection<UUID> placeIds) {
        Map<UUID, Place> found = placesById(placeIds);
        for (UUID placeId : placeIds) {
            Place place = found.get(placeId);
            if (place == null || !place.isListed()) {
                throw new IllegalStateException("Place with id=%s is not a place travellers can go to"
                        .formatted(placeId));
            }
        }
    }

    TripPlanResponse toResponse(TripPlan plan) {
        Map<UUID, Place> places = placesById(concat(plan.getPlaceIds(), plan.getMustSeePlaceIds()));
        return new TripPlanResponse(plan.getId(), plan.getOwnerType(), plan.getOwnerId(), plan.getCreatedByUserId(),
                plan.getTitle(), plan.getStartDate(), plan.getEndDate(), plan.dayCount(), plan.getAdults(),
                plan.getChildren(), plan.getBudgetLevel(), plan.getPace(),
                plan.getInterests().stream().sorted().toList(),
                refs(plan.getPlaceIds(), places), refs(plan.getMustSeePlaceIds(), places), plan.getNotes(),
                plan.getLanguageCode(), plan.getCreatedAt(), plan.getUpdatedAt());
    }

    private List<PlaceRef> refs(Collection<UUID> ids, Map<UUID, Place> places) {
        return ids.stream().filter(places::containsKey).map(id -> placeMapper.toRef(places.get(id))).toList();
    }

    private Map<UUID, Place> placesById(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return placeRepository.findAllById(ids).stream().filter(place -> place.getDeletedAt() == null)
                .collect(Collectors.toMap(Place::getId, Function.identity()));
    }

    private static List<UUID> concat(Collection<UUID> first, Collection<UUID> second) {
        List<UUID> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }
}
