package com.triphuddle.api.destination.mapper;

import com.triphuddle.api.destination.dto.CategoryRef;
import com.triphuddle.api.destination.dto.PhotoResponse;
import com.triphuddle.api.destination.dto.PlaceNameResponse;
import com.triphuddle.api.destination.dto.PlaceRef;
import com.triphuddle.api.destination.dto.PlaceResponse;
import com.triphuddle.api.destination.dto.PlaceSummaryResponse;
import com.triphuddle.api.destination.entity.Category;
import com.triphuddle.api.destination.entity.Place;
import com.triphuddle.api.destination.entity.PlaceName;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Maps {@link Place} to its responses.
 */
@Component
public class PlaceMapper {

    /**
     * Convert a place to a short reference.
     *
     * @param place the place
     * @return the reference
     */
    public PlaceRef toRef(Place place) {
        return new PlaceRef(place.getId(), place.getSlug(), place.getName(), place.getKind());
    }

    /**
     * Convert a place to a list entry.
     *
     * @param place the place, with its parent and categories
     * @param distanceMeters its distance from the searched point, or null
     * @param photo its primary photo, or null
     * @return the list entry
     */
    public PlaceSummaryResponse toSummary(Place place, Double distanceMeters, PhotoResponse photo) {
        return new PlaceSummaryResponse(place.getId(), place.getSlug(), place.getName(), place.getKind(),
                place.getCountryCode(), place.getParent() == null ? null : toRef(place.getParent()),
                place.getLatitude(), place.getLongitude(), place.getPopulation(), place.isFeatured(),
                categoriesOf(place), distanceMeters == null ? null : Math.round(distanceMeters / 10.0) / 100.0, photo);
    }

    /**
     * Convert a place to its full response.
     *
     * @param place the place
     * @param ancestors the places it is in, from the top down
     * @param photos its photos, the primary one first
     * @return the response
     */
    public PlaceResponse toResponse(Place place, List<Place> ancestors, List<PhotoResponse> photos) {
        List<PlaceNameResponse> names = place.getNames().stream()
                .sorted(Comparator.comparing(PlaceName::isAlias).thenComparing(PlaceName::getLanguageCode)
                        .thenComparing(PlaceName::getName))
                .map(name -> new PlaceNameResponse(name.getLanguageCode(), name.getName(), name.isAlias()))
                .toList();
        return new PlaceResponse(place.getId(), place.getSlug(), place.getName(), place.getKind(), place.getStatus(),
                place.getCountryCode(), ancestors.stream().map(this::toRef).toList(), place.getLatitude(),
                place.getLongitude(), place.getPopulation(), place.isFeatured(), place.getWikidataId(),
                place.getOsmId(), names, categoriesOf(place), place.getTags().stream().sorted().toList(), photos,
                place.getCreatedAt(), place.getUpdatedAt());
    }

    private static List<CategoryRef> categoriesOf(Place place) {
        return place.getCategories().stream()
                .filter(category -> category.getDeletedAt() == null)
                .sorted(Comparator.comparing(Category::getDepth).thenComparing(Category::getName))
                .map(category -> new CategoryRef(category.getId(), category.getSlug(), category.getName()))
                .toList();
    }
}
