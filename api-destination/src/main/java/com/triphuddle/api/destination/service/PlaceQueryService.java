package com.triphuddle.api.destination.service;

import com.triphuddle.api.destination.config.DestinationProperties;
import com.triphuddle.api.destination.dto.PhotoResponse;
import com.triphuddle.api.destination.dto.PlaceResponse;
import com.triphuddle.api.destination.dto.PlaceSearchFilter;
import com.triphuddle.api.destination.dto.PlaceSummaryResponse;
import com.triphuddle.api.destination.entity.Category;
import com.triphuddle.api.destination.entity.Place;
import com.triphuddle.api.destination.entity.PlaceStatus;
import com.triphuddle.api.destination.mapper.PlaceMapper;
import com.triphuddle.api.destination.repository.CategoryRepository;
import com.triphuddle.api.destination.repository.PlaceRepository;
import com.triphuddle.api.destination.repository.PlaceSearchHit;
import com.triphuddle.api.destination.repository.PlaceSearchQuery;
import com.triphuddle.api.destination.repository.PlaceSearchRepository;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads places: one with its breadcrumb, or a search by words, area, distance, subtree, category or tag.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceQueryService {

    private final PlaceRepository placeRepository;
    private final CategoryRepository categoryRepository;
    private final PlaceSearchRepository placeSearchRepository;
    private final PlaceMapper placeMapper;
    private final DestinationProperties properties;
    private final PhotoService photoService;

    /**
     * Get a place by its ID; a merged one leads to the place it was merged into.
     *
     * @param id the ID of the place
     * @param isStaff whether the caller may see hidden places
     * @return the place
     */
    public PlaceResponse getById(UUID id, boolean isStaff) {
        return toResponse(visible(placeRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> notFound(id)),
                isStaff));
    }

    /**
     * Get a place by its slug.
     *
     * @param slug the slug
     * @param isStaff whether the caller may see hidden places
     * @return the place
     */
    public PlaceResponse getBySlug(String slug, boolean isStaff) {
        Place place = placeRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Place with slug=%s not found".formatted(slug)));
        return toResponse(visible(place, isStaff));
    }

    /**
     * Search places.
     *
     * @param filter the words, area, point, subtree, category, tag and page
     * @param isStaff whether the caller may include hidden places
     * @return a page of places
     */
    public Paginated<PlaceSummaryResponse> search(PlaceSearchFilter filter, boolean isStaff) {
        Page<PlaceSearchHit> hits = placeSearchRepository.search(toQuery(filter, isStaff), filter.toPageable());
        Map<UUID, Place> places = placeRepository.findAllWithParent(
                        hits.getContent().stream().map(PlaceSearchHit::placeId).toList()).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        Map<UUID, PhotoResponse> photos = photoService.primaryPhotosOf(places.keySet());
        return Paginated.of(hits, page -> page.stream()
                .filter(hit -> places.containsKey(hit.placeId()))
                .map(hit -> placeMapper.toSummary(places.get(hit.placeId()), hit.distanceMeters(),
                        photos.get(hit.placeId())))
                .toList());
    }

    /**
     * Get the list entries of places customers may see, in the order given; others are left out.
     *
     * @param ids the IDs of the places
     * @return the entries
     */
    public List<PlaceSummaryResponse> summariesOf(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, Place> places = placeRepository.findAllWithParent(ids).stream().filter(Place::isListed)
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        Map<UUID, PhotoResponse> photos = photoService.primaryPhotosOf(places.keySet());
        return ids.stream().filter(places::containsKey)
                .map(id -> placeMapper.toSummary(places.get(id), null, photos.get(id))).toList();
    }

    private PlaceSearchQuery toQuery(PlaceSearchFilter filter, boolean isStaff) {
        String text = filter.getQ() == null || filter.getQ().isBlank() ? null : filter.getQ().strip();
        String withinPath = null;
        if (filter.getWithinId() != null) {
            withinPath = placeRepository.findByIdAndDeletedAtIsNull(filter.getWithinId())
                    .orElseThrow(() -> notFound(filter.getWithinId())).getPath();
        }
        String categoryPath = null;
        if (filter.getCategoryId() != null) {
            categoryPath = categoryRepository.findByIdAndDeletedAtIsNull(filter.getCategoryId())
                    .map(Category::getPath)
                    .orElseThrow(() -> new ResourceNotFoundException("Category with id=%s not found"
                            .formatted(filter.getCategoryId())));
        }
        Double radiusMeters = null;
        if (filter.getRadiusKm() != null) {
            if (filter.getLatitude() == null || filter.getLongitude() == null) {
                throw new IllegalStateException("A radius needs a latitude and a longitude");
            }
            radiusMeters = Math.min(filter.getRadiusKm(), properties.maxRadiusKm()) * 1000;
        }
        long edges = Arrays.asList(filter.getWest(), filter.getSouth(), filter.getEast(), filter.getNorth()).stream()
                .filter(edge -> edge != null).count();
        if (edges != 0 && edges != 4) {
            throw new IllegalStateException("A map area needs all of west, south, east and north");
        }
        return new PlaceSearchQuery(text,
                filter.getCountryCode() == null ? null : filter.getCountryCode().toUpperCase(Locale.ROOT),
                filter.getKind(), categoryPath,
                filter.getTag() == null || filter.getTag().isBlank() ? null : filter.getTag().strip(),
                filter.getWithinId(), withinPath, filter.getParentId(), filter.getIsFeatured(),
                filter.getLatitude(), filter.getLongitude(), radiusMeters,
                filter.getWest(), filter.getSouth(), filter.getEast(), filter.getNorth(),
                isStaff && Boolean.TRUE.equals(filter.getIsIncludingHidden()));
    }

    /**
     * Get a place a caller may see, following merges.
     *
     * @param id the ID of the place
     * @param isStaff whether the caller may see hidden places
     * @return the place
     */
    public Place visiblePlace(UUID id, boolean isStaff) {
        return visible(placeRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> notFound(id)), isStaff);
    }

    // A merged place answers with the place it went into; a hidden one exists only for staff.
    private Place visible(Place place, boolean isStaff) {
        Place current = place;
        for (int hops = 0; current.getStatus() == PlaceStatus.MERGED && current.getMergedIntoId() != null
                && hops < 5; hops++) {
            UUID target = current.getMergedIntoId();
            current = placeRepository.findByIdAndDeletedAtIsNull(target).orElseThrow(() -> notFound(target));
        }
        if (!current.isListed() && !(isStaff && current.getStatus() == PlaceStatus.HIDDEN)) {
            throw notFound(place.getId());
        }
        return current;
    }

    PlaceResponse toResponse(Place place) {
        return placeMapper.toResponse(place, ancestorsOf(place), photoService.photosOf(place.getId()));
    }

    /**
     * Get the places a place is in, from the top of its country down.
     *
     * @param place the place
     * @return its ancestors
     */
    public List<Place> ancestorsOf(Place place) {
        List<UUID> ids = Arrays.stream(place.getPath().split("/"))
                .filter(part -> !part.isBlank())
                .map(UUID::fromString)
                .filter(id -> !id.equals(place.getId()))
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, Place> byId = placeRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        return ids.stream().filter(byId::containsKey).map(byId::get).toList();
    }

    static ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Place with id=%s not found".formatted(id));
    }
}
