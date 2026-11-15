package com.triphuddle.api.destination.controller;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.destination.dto.ItineraryItemRequest;
import com.triphuddle.api.destination.dto.ItineraryResponse;
import com.triphuddle.api.destination.dto.TripPlanRequest;
import com.triphuddle.api.destination.dto.TripPlanResponse;
import com.triphuddle.api.destination.trip.ItineraryService;
import com.triphuddle.api.destination.trip.TripPlanService;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Trip plans and their itineraries under {@code /api/v1/destinations}.
 */
@RestController
@RequestMapping("/api/v1/destinations")
@RequiredArgsConstructor
@Tag(name = "Trips")
public class TripController {

    private final TripPlanService planService;
    private final ItineraryService itineraryService;

    /**
     * Create a plan for the caller, or for a group they belong to.
     *
     * @param request the plan
     * @param authentication the caller
     * @return the plan
     */
    @PostMapping("/trip-plans")
    @Operation(summary = "Create a trip plan")
    @PreAuthorize("@TripPolicy.canCreate(authentication, #request)")
    public ResponseEntity<TripPlanResponse> create(@Valid @RequestBody TripPlanRequest request,
                                                   Authentication authentication) {
        return new ResponseEntity<>(planService.create(request, SecurityUtils.extractUserId(authentication)),
                HttpStatus.CREATED);
    }

    /**
     * Get the caller's own plans, the soonest first.
     *
     * @param filter the page
     * @param authentication the caller
     * @return a page of plans
     */
    @GetMapping("/trip-plans/mine")
    @Operation(summary = "Get my trip plans")
    @PreAuthorize("@TripPolicy.canGetMine(authentication)")
    public ResponseEntity<Paginated<TripPlanResponse>> mine(@Valid @ModelAttribute PaginationFilter filter,
                                                            Authentication authentication) {
        return ResponseEntity.ok(planService.getForOwner(LicenseTarget.USER,
                SecurityUtils.extractUserId(authentication), filter));
    }

    /**
     * Get a group's plans, the soonest first.
     *
     * @param groupId the ID of the group
     * @param filter the page
     * @return a page of plans
     */
    @GetMapping("/trip-plans/groups/{groupId}")
    @Operation(summary = "Get a group's trip plans")
    @PreAuthorize("@TripPolicy.canGetForGroup(authentication, #groupId)")
    public ResponseEntity<Paginated<TripPlanResponse>> forGroup(@PathVariable UUID groupId,
                                                                @Valid @ModelAttribute PaginationFilter filter) {
        return ResponseEntity.ok(planService.getForOwner(LicenseTarget.GROUP, groupId, filter));
    }

    /**
     * Get a plan.
     *
     * @param planId the ID of the plan
     * @return the plan
     */
    @GetMapping("/trip-plans/{planId}")
    @Operation(summary = "Get a trip plan")
    @PreAuthorize("@TripPolicy.canGetById(authentication, #planId)")
    public ResponseEntity<TripPlanResponse> get(@PathVariable UUID planId) {
        return ResponseEntity.ok(planService.get(planId));
    }

    /**
     * Change a plan.
     *
     * @param planId the ID of the plan
     * @param request the new details
     * @return the plan
     */
    @PutMapping("/trip-plans/{planId}")
    @Operation(summary = "Update a trip plan")
    @PreAuthorize("@TripPolicy.canUse(authentication, #planId)")
    public ResponseEntity<TripPlanResponse> update(@PathVariable UUID planId,
                                                   @Valid @RequestBody TripPlanRequest request) {
        return ResponseEntity.ok(planService.update(planId, request));
    }

    /**
     * Delete a plan: its creator, or an admin of its group.
     *
     * @param planId the ID of the plan
     * @return no content
     */
    @DeleteMapping("/trip-plans/{planId}")
    @Operation(summary = "Delete a trip plan")
    @PreAuthorize("@TripPolicy.canDelete(authentication, #planId)")
    public ResponseEntity<Void> delete(@PathVariable UUID planId) {
        planService.delete(planId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Have an itinerary written for a plan; follow it until READY, or wait for the notification.
     *
     * @param planId the ID of the plan
     * @param authentication the caller
     * @return the itinerary, GENERATING
     */
    @PostMapping("/trip-plans/{planId}/itineraries")
    @Operation(summary = "Write an itinerary")
    @PreAuthorize("@TripPolicy.canUse(authentication, #planId)")
    public ResponseEntity<ItineraryResponse> generate(@PathVariable UUID planId, Authentication authentication) {
        return new ResponseEntity<>(itineraryService.generate(planId, SecurityUtils.extractUserId(authentication)),
                HttpStatus.ACCEPTED);
    }

    /**
     * Get a plan's itineraries, the newest first.
     *
     * @param planId the ID of the plan
     * @return the itineraries
     */
    @GetMapping("/trip-plans/{planId}/itineraries")
    @Operation(summary = "Get a plan's itineraries")
    @PreAuthorize("@TripPolicy.canGetById(authentication, #planId)")
    public ResponseEntity<List<ItineraryResponse>> itineraries(@PathVariable UUID planId) {
        return ResponseEntity.ok(itineraryService.getForPlan(planId));
    }

    /**
     * Get an itinerary.
     *
     * @param itineraryId the ID of the itinerary
     * @return the itinerary
     */
    @GetMapping("/itineraries/{itineraryId}")
    @Operation(summary = "Get an itinerary")
    @PreAuthorize("@TripPolicy.canReadItinerary(authentication, #itineraryId)")
    public ResponseEntity<ItineraryResponse> itinerary(@PathVariable UUID itineraryId) {
        return ResponseEntity.ok(itineraryService.get(itineraryId));
    }

    /**
     * Have one day written again, optionally as the travellers ask.
     *
     * @param itineraryId the ID of the itinerary
     * @param dayNumber the day
     * @param instructions e.g. "more food, less walking", or null
     * @param authentication the caller
     * @return the itinerary, with the day being rewritten
     */
    @PostMapping("/itineraries/{itineraryId}/days/{dayNumber}/regenerate")
    @Operation(summary = "Rewrite one day")
    @PreAuthorize("@TripPolicy.canUseItinerary(authentication, #itineraryId)")
    public ResponseEntity<ItineraryResponse> regenerateDay(@PathVariable UUID itineraryId, @PathVariable int dayNumber,
                                                           @RequestParam(required = false) @Size(max = 500)
                                                           String instructions, Authentication authentication) {
        return new ResponseEntity<>(itineraryService.regenerateDay(itineraryId, dayNumber, instructions,
                SecurityUtils.extractUserId(authentication)), HttpStatus.ACCEPTED);
    }

    /**
     * Put a day's items in a new order.
     *
     * @param itineraryId the ID of the itinerary
     * @param dayNumber the day
     * @param itemIds every item of the day, in the new order
     * @return the itinerary
     */
    @PutMapping("/itineraries/{itineraryId}/days/{dayNumber}/order")
    @Operation(summary = "Reorder a day")
    @PreAuthorize("@TripPolicy.canUseItinerary(authentication, #itineraryId)")
    public ResponseEntity<ItineraryResponse> reorder(@PathVariable UUID itineraryId, @PathVariable int dayNumber,
                                                     @RequestBody List<UUID> itemIds) {
        return ResponseEntity.ok(itineraryService.reorder(itineraryId, dayNumber, itemIds));
    }

    /**
     * Add something to a day.
     *
     * @param itineraryId the ID of the itinerary
     * @param dayNumber the day
     * @param request the item
     * @return the itinerary
     */
    @PostMapping("/itineraries/{itineraryId}/days/{dayNumber}/items")
    @Operation(summary = "Add to a day")
    @PreAuthorize("@TripPolicy.canUseItinerary(authentication, #itineraryId)")
    public ResponseEntity<ItineraryResponse> addItem(@PathVariable UUID itineraryId, @PathVariable int dayNumber,
                                                     @Valid @RequestBody ItineraryItemRequest request) {
        return ResponseEntity.ok(itineraryService.addItem(itineraryId, dayNumber, request));
    }

    /**
     * Remove something from a day.
     *
     * @param itineraryId the ID of the itinerary
     * @param itemId the ID of the item
     * @return the itinerary
     */
    @DeleteMapping("/itineraries/{itineraryId}/items/{itemId}")
    @Operation(summary = "Remove from a day")
    @PreAuthorize("@TripPolicy.canUseItinerary(authentication, #itineraryId)")
    public ResponseEntity<ItineraryResponse> removeItem(@PathVariable UUID itineraryId, @PathVariable UUID itemId) {
        return ResponseEntity.ok(itineraryService.removeItem(itineraryId, itemId));
    }
}
