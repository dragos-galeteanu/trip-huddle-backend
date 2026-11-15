package com.triphuddle.api.destination.controller;

import com.triphuddle.api.destination.semantic.SemanticSearchService;
import com.triphuddle.api.destination.entity.PlaceKind;
import com.triphuddle.api.destination.dto.SemanticSearchResponse;
import com.triphuddle.api.destination.dto.MergePlaceRequest;
import com.triphuddle.api.destination.dto.MovePlaceRequest;
import com.triphuddle.api.destination.dto.PhotoResponse;
import com.triphuddle.api.destination.dto.PlaceRequest;
import com.triphuddle.api.destination.dto.PlaceResponse;
import com.triphuddle.api.destination.dto.PlaceSearchFilter;
import com.triphuddle.api.destination.dto.PlaceSummaryResponse;
import com.triphuddle.api.destination.security.DestinationAuthorizationPolicy;
import com.triphuddle.api.destination.service.PhotoService;
import com.triphuddle.api.destination.service.PlaceAdminService;
import com.triphuddle.api.destination.service.PlaceQueryService;
import com.triphuddle.common.dto.Paginated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * Places under {@code /api/v1/destinations/places}: search and read with a license, manage as staff.
 */
@RestController
@RequestMapping("/api/v1/destinations/places")
@RequiredArgsConstructor
@Tag(name = "Places")
public class PlaceController {

    private final PlaceQueryService placeQueryService;
    private final PlaceAdminService placeAdminService;
    private final DestinationAuthorizationPolicy destinationPolicy;
    private final PhotoService photoService;
    private final SemanticSearchService semanticSearchService;

    /**
     * Search places by words, area, distance, subtree, category or tag.
     *
     * @param filter the filters and page
     * @param authentication the caller
     * @return a page of places
     */
    @GetMapping
    @Operation(summary = "Search places")
    @PreAuthorize("@DestinationPolicy.canBrowse(authentication, #filter.groupId)")
    public ResponseEntity<Paginated<PlaceSummaryResponse>> search(@Valid @ModelAttribute PlaceSearchFilter filter,
                                                                  Authentication authentication) {
        return ResponseEntity.ok(placeQueryService.search(filter, destinationPolicy.isStaff(authentication)));
    }

    /**
     * Get a place with its breadcrumb, names, categories and tags.
     *
     * @param id the ID of the place
     * @param groupId the group the caller acts for, or null
     * @param authentication the caller
     * @return the place
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a place")
    @PreAuthorize("@DestinationPolicy.canBrowse(authentication, #groupId)")
    public ResponseEntity<PlaceResponse> getById(@PathVariable UUID id,
                                                 @RequestParam(required = false) UUID groupId,
                                                 Authentication authentication) {
        return ResponseEntity.ok(placeQueryService.getById(id, destinationPolicy.isStaff(authentication)));
    }

    /**
     * Get a place by its slug.
     *
     * @param slug the slug, e.g. pt-porto
     * @param groupId the group the caller acts for, or null
     * @param authentication the caller
     * @return the place
     */
    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get a place by its slug")
    @PreAuthorize("@DestinationPolicy.canBrowse(authentication, #groupId)")
    public ResponseEntity<PlaceResponse> getBySlug(@PathVariable String slug,
                                                   @RequestParam(required = false) UUID groupId,
                                                   Authentication authentication) {
        return ResponseEntity.ok(placeQueryService.getBySlug(slug, destinationPolicy.isStaff(authentication)));
    }

    /**
     * Create a place.
     *
     * @param request the place
     * @return the created place
     */
    @PostMapping
    @Operation(summary = "Create a place")
    @PreAuthorize("@DestinationPolicy.canManage(authentication)")
    public ResponseEntity<PlaceResponse> create(@Valid @RequestBody PlaceRequest request) {
        return new ResponseEntity<>(placeAdminService.create(request), HttpStatus.CREATED);
    }

    /**
     * Change a place's details.
     *
     * @param id the ID of the place
     * @param request the new details
     * @return the place
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update a place")
    @PreAuthorize("@DestinationPolicy.canManage(authentication)")
    public ResponseEntity<PlaceResponse> update(@PathVariable UUID id, @Valid @RequestBody PlaceRequest request) {
        return ResponseEntity.ok(placeAdminService.update(id, request));
    }

    /**
     * Move a place, with everything in it.
     *
     * @param id the ID of the place
     * @param request the new parent
     * @return the moved place
     */
    @PostMapping("/{id}/move")
    @Operation(summary = "Move a place")
    @PreAuthorize("@DestinationPolicy.canManage(authentication)")
    public ResponseEntity<PlaceResponse> move(@PathVariable UUID id, @RequestBody MovePlaceRequest request) {
        return ResponseEntity.ok(placeAdminService.move(id, request.getParentId()));
    }

    /**
     * Merge a duplicate into another place.
     *
     * @param id the ID of the duplicate
     * @param request the place it is merged into
     * @return the place it was merged into
     */
    @PostMapping("/{id}/merge")
    @Operation(summary = "Merge a duplicate place")
    @PreAuthorize("@DestinationPolicy.canManage(authentication)")
    public ResponseEntity<PlaceResponse> merge(@PathVariable UUID id, @Valid @RequestBody MergePlaceRequest request) {
        return ResponseEntity.ok(placeAdminService.merge(id, request.getTargetId()));
    }

    /**
     * Hide a place from customers.
     *
     * @param id the ID of the place
     * @return the place
     */
    @PostMapping("/{id}/hide")
    @Operation(summary = "Hide a place")
    @PreAuthorize("@DestinationPolicy.canManage(authentication)")
    public ResponseEntity<PlaceResponse> hide(@PathVariable UUID id) {
        return ResponseEntity.ok(placeAdminService.setHidden(id, true));
    }

    /**
     * Show a hidden place again.
     *
     * @param id the ID of the place
     * @return the place
     */
    @PostMapping("/{id}/unhide")
    @Operation(summary = "Show a hidden place")
    @PreAuthorize("@DestinationPolicy.canManage(authentication)")
    public ResponseEntity<PlaceResponse> unhide(@PathVariable UUID id) {
        return ResponseEntity.ok(placeAdminService.setHidden(id, false));
    }

    /**
     * Feature a place.
     *
     * @param id the ID of the place
     * @return the place
     */
    @PostMapping("/{id}/feature")
    @Operation(summary = "Feature a place")
    @PreAuthorize("@DestinationPolicy.canManage(authentication)")
    public ResponseEntity<PlaceResponse> feature(@PathVariable UUID id) {
        return ResponseEntity.ok(placeAdminService.setFeatured(id, true));
    }

    /**
     * Stop featuring a place.
     *
     * @param id the ID of the place
     * @return the place
     */
    @PostMapping("/{id}/unfeature")
    @Operation(summary = "Stop featuring a place")
    @PreAuthorize("@DestinationPolicy.canManage(authentication)")
    public ResponseEntity<PlaceResponse> unfeature(@PathVariable UUID id) {
        return ResponseEntity.ok(placeAdminService.setFeatured(id, false));
    }

    /**
     * Make a photo the place's main one.
     *
     * @param id the ID of the place
     * @param photoId the ID of the photo
     * @return the place's photos
     */
    @PostMapping("/{id}/photos/{photoId}/primary")
    @Operation(summary = "Make a photo the main one")
    @PreAuthorize("@DestinationPolicy.canManage(authentication)")
    public ResponseEntity<List<PhotoResponse>> makePrimary(@PathVariable UUID id, @PathVariable UUID photoId) {
        return ResponseEntity.ok(photoService.makePrimary(id, photoId));
    }

    /**
     * Remove a photo; imports won't add it again.
     *
     * @param id the ID of the place
     * @param photoId the ID of the photo
     * @return no content
     */
    @DeleteMapping("/{id}/photos/{photoId}")
    @Operation(summary = "Remove a photo")
    @PreAuthorize("@DestinationPolicy.canManage(authentication)")
    public ResponseEntity<Void> deletePhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        photoService.delete(id, photoId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Delete a place with nothing in it.
     *
     * @param id the ID of the place
     * @return no content
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a place")
    @PreAuthorize("@DestinationPolicy.canManage(authentication)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        placeAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Find places by what a description means, e.g. "quiet beach town with seafood"; a new description comes back
     * GENERATING once, so ask again shortly.
     *
     * @param q the description
     * @param countryCode only in this country, or null
     * @param kind only of this kind, or null
     * @param withinId only within this place, or null
     * @param limit the most results
     * @param groupId the group the caller acts for, or null
     * @return the places, the closest first
     */
    @GetMapping("/semantic")
    @Operation(summary = "Search places by meaning")
    @PreAuthorize("@DestinationPolicy.canBrowse(authentication, #groupId)")
    public ResponseEntity<SemanticSearchResponse> semantic(@RequestParam String q,
                                                           @RequestParam(required = false) String countryCode,
                                                           @RequestParam(required = false) PlaceKind kind,
                                                           @RequestParam(required = false) UUID withinId,
                                                           @RequestParam(defaultValue = "20") int limit,
                                                           @RequestParam(required = false) UUID groupId) {
        return ResponseEntity.ok(semanticSearchService.search(q, countryCode, kind, withinId, limit));
    }

    /**
     * Find the places most like one.
     *
     * @param id the ID of the place
     * @param isSameCountry whether to stay in its country
     * @param limit the most results
     * @param groupId the group the caller acts for, or null
     * @return the places, the closest first
     */
    @GetMapping("/{id}/similar")
    @Operation(summary = "Find similar places")
    @PreAuthorize("@DestinationPolicy.canBrowse(authentication, #groupId)")
    public ResponseEntity<SemanticSearchResponse> similar(@PathVariable UUID id,
                                                          @RequestParam(defaultValue = "false") boolean isSameCountry,
                                                          @RequestParam(defaultValue = "10") int limit,
                                                          @RequestParam(required = false) UUID groupId) {
        return ResponseEntity.ok(semanticSearchService.similar(id, isSameCountry, limit));
    }
}
