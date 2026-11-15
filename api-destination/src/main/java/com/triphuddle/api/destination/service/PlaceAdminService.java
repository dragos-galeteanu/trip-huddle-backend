package com.triphuddle.api.destination.service;

import com.triphuddle.api.destination.dto.PlaceNameRequest;
import com.triphuddle.api.destination.dto.PlaceRequest;
import com.triphuddle.api.destination.dto.PlaceResponse;
import com.triphuddle.api.destination.entity.Category;
import com.triphuddle.api.destination.entity.Place;
import com.triphuddle.api.destination.entity.PlaceName;
import com.triphuddle.api.destination.entity.PlaceStatus;
import com.triphuddle.api.destination.repository.CategoryRepository;
import com.triphuddle.api.destination.repository.CountryCopyRepository;
import com.triphuddle.api.destination.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Staff changes to places: create, edit, hide, feature, move within the tree, merge duplicates and delete.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PlaceAdminService {

    private final PlaceRepository placeRepository;
    private final CategoryRepository categoryRepository;
    private final CountryCopyRepository countryCopyRepository;
    private final PlaceQueryService placeQueryService;

    /**
     * Create a place under an active country, and under a parent in that country if given.
     *
     * @param request the place
     * @return the created place
     */
    public PlaceResponse create(PlaceRequest request) {
        if (request.getCountryCode() == null) {
            throw new IllegalStateException("A country code is required");
        }
        String countryCode = request.getCountryCode().toUpperCase(Locale.ROOT);
        if (!countryCopyRepository.existsByAlpha2AndDisabledAtIsNullAndDeletedAtIsNull(countryCode)) {
            throw new IllegalStateException("Country %s is not an active country".formatted(countryCode));
        }
        Place parent = request.getParentId() == null ? null : parentFor(request.getParentId(), countryCode);

        Place place = new Place();
        place.setCountryCode(countryCode);
        place.setParent(parent);
        apply(place, request);
        place.setSlug(request.getSlug() != null ? requireFreeSlug(request.getSlug(), null)
                : freeSlug(countryCode.toLowerCase(Locale.ROOT) + "-" + Slugs.of(request.getName())));
        Place saved = placeRepository.save(place);
        // The ID is known once saved; the path ends with it.
        saved.setPath((parent == null ? "/" : parent.getPath()) + saved.getId() + "/");
        saved.setDepth(parent == null ? 0 : parent.getDepth() + 1);
        log.info("Place created [id={}, countryCode={}, kind={}, slug={}]", saved.getId(), countryCode,
                saved.getKind(), saved.getSlug());
        return placeQueryService.toResponse(saved);
    }

    /**
     * Change a place's details; its names, categories and tags are replaced as given.
     *
     * @param id the ID of the place
     * @param request the new details
     * @return the place
     */
    public PlaceResponse update(UUID id, PlaceRequest request) {
        Place place = lock(id);
        requireNotMerged(place);
        apply(place, request);
        if (request.getSlug() != null && !request.getSlug().equals(place.getSlug())) {
            place.setSlug(requireFreeSlug(request.getSlug(), id));
        }
        log.info("Place updated [id={}]", id);
        return placeQueryService.toResponse(place);
    }

    /**
     * Hide a place from customers, or show it again.
     *
     * @param id the ID of the place
     * @param isHidden true to hide it
     * @return the place
     */
    public PlaceResponse setHidden(UUID id, boolean isHidden) {
        Place place = lock(id);
        requireNotMerged(place);
        place.setStatus(isHidden ? PlaceStatus.HIDDEN : PlaceStatus.ACTIVE);
        log.info("Place visibility changed [id={}, isHidden={}]", id, isHidden);
        return placeQueryService.toResponse(place);
    }

    /**
     * Feature a place, or stop featuring it; featured places come first in lists without words.
     *
     * @param id the ID of the place
     * @param isFeatured true to feature it
     * @return the place
     */
    public PlaceResponse setFeatured(UUID id, boolean isFeatured) {
        Place place = lock(id);
        requireNotMerged(place);
        place.setFeatured(isFeatured);
        return placeQueryService.toResponse(place);
    }

    /**
     * Move a place, with everything in it, under another parent in its country, or to the top.
     *
     * @param id the ID of the place
     * @param parentId the new parent, or null for the top of the country
     * @return the moved place
     */
    public PlaceResponse move(UUID id, UUID parentId) {
        Place place = lock(id);
        requireNotMerged(place);
        Place parent = parentId == null ? null : parentFor(parentId, place.getCountryCode());
        if (parent != null && parent.getPath().startsWith(place.getPath())) {
            throw new IllegalStateException("A place can't be moved into itself or a place inside it");
        }
        String oldPrefix = place.getPath();
        String newPrefix = (parent == null ? "/" : parent.getPath()) + place.getId() + "/";
        int depthDelta = (parent == null ? 0 : parent.getDepth() + 1) - place.getDepth();
        place.setParent(parent);
        placeRepository.saveAndFlush(place);
        int moved = placeRepository.movePaths(oldPrefix, newPrefix, depthDelta);
        log.info("Place moved [id={}, parentId={}, placesMoved={}]", id, parentId, moved);
        return placeQueryService.toResponse(placeRepository.findByIdAndDeletedAtIsNull(id).orElseThrow());
    }

    /**
     * Merge a duplicate into another place: its children, names (as aliases), categories, tags and source IDs go
     * to the target, and it becomes MERGED, pointing at the target.
     *
     * @param id the ID of the duplicate
     * @param targetId the ID of the place it is merged into
     * @return the target
     */
    public PlaceResponse merge(UUID id, UUID targetId) {
        if (id.equals(targetId)) {
            throw new IllegalStateException("A place can't be merged into itself");
        }
        // Locked in ID order, so two merges between the same places can't deadlock.
        Place first = lock(id.compareTo(targetId) < 0 ? id : targetId);
        Place second = lock(id.compareTo(targetId) < 0 ? targetId : id);
        Place source = first.getId().equals(id) ? first : second;
        Place target = source == first ? second : first;
        requireNotMerged(source);
        requireNotMerged(target);
        if (!source.getCountryCode().equals(target.getCountryCode())) {
            throw new IllegalStateException("Places in different countries can't be merged");
        }
        if (target.getPath().startsWith(source.getPath())) {
            throw new IllegalStateException("A place can't be merged into a place inside it");
        }

        for (PlaceName name : source.getNames()) {
            addName(target, name.getLanguageCode(), name.getName(), true);
        }
        addName(target, PlaceName.ANY_LANGUAGE, source.getName(), true);
        target.getCategories().addAll(source.getCategories());
        target.getTags().addAll(source.getTags());
        // The source IDs are unique, so the duplicate lets go of them first.
        String wikidataId = source.getWikidataId();
        String osmId = source.getOsmId();
        source.setWikidataId(null);
        source.setOsmId(null);
        source.setStatus(PlaceStatus.MERGED);
        source.setMergedIntoId(target.getId());
        placeRepository.saveAndFlush(source);
        if (target.getWikidataId() == null) {
            target.setWikidataId(wikidataId);
        }
        if (target.getOsmId() == null) {
            target.setOsmId(osmId);
        }
        placeRepository.saveAndFlush(target);

        String sourcePath = source.getPath();
        int depthDelta = target.getDepth() - source.getDepth();
        placeRepository.reparentChildren(id, targetId);
        // The duplicate's subtree now hangs from the target; the duplicate itself is re-pointed below.
        placeRepository.movePaths(sourcePath, target.getPath(), depthDelta);
        Place merged = placeRepository.findByIdAndDeletedAtIsNull(id).orElseThrow();
        Place into = placeRepository.findByIdAndDeletedAtIsNull(targetId).orElseThrow();
        merged.setParent(into);
        merged.setPath(into.getPath() + merged.getId() + "/");
        merged.setDepth(into.getDepth() + 1);
        log.info("Place merged [id={}, targetId={}]", id, targetId);
        return placeQueryService.toResponse(into);
    }

    /**
     * Delete a place that has nothing in it; one with children is moved or merged instead.
     *
     * @param id the ID of the place
     */
    public void delete(UUID id) {
        Place place = lock(id);
        if (placeRepository.existsByParentIdAndDeletedAtIsNull(id)) {
            throw new IllegalStateException("Place with id=%s has places in it; move or merge them first"
                    .formatted(id));
        }
        place.setDeletedAt(Instant.now());
        log.info("Place deleted [id={}]", id);
    }

    private void apply(Place place, PlaceRequest request) {
        place.setKind(request.getKind());
        place.setName(request.getName().strip());
        place.setLatitude(request.getLatitude());
        place.setLongitude(request.getLongitude());
        if ((request.getLatitude() == null) != (request.getLongitude() == null)) {
            throw new IllegalStateException("Give both latitude and longitude, or neither");
        }
        if (request.getWikidataId() != null
                && placeRepository.isWikidataIdTaken(request.getWikidataId(), place.getId())) {
            throw new IllegalStateException("Wikidata ID %s belongs to another place; merge them instead"
                    .formatted(request.getWikidataId()));
        }
        if (request.getOsmId() != null && placeRepository.isOsmIdTaken(request.getOsmId(), place.getId())) {
            throw new IllegalStateException("OpenStreetMap ID %s belongs to another place; merge them instead"
                    .formatted(request.getOsmId()));
        }
        place.setWikidataId(request.getWikidataId());
        place.setOsmId(request.getOsmId());
        place.setPopulation(request.getPopulation());
        replaceNames(place, request.getNames());
        place.setCategories(categoriesFor(request.getCategoryIds()));
        place.setTags(new HashSet<>(request.getTags()));
    }

    // Names are matched by language and name, so unchanged rows stay; the primary name is always one of them.
    private static void replaceNames(Place place, List<PlaceNameRequest> requested) {
        Set<String> wanted = new HashSet<>();
        for (PlaceNameRequest name : requested) {
            wanted.add(key(name.getLanguageCode(), name.getName().strip()));
        }
        String primary = key(PlaceName.ANY_LANGUAGE, place.getName());
        boolean isPrimaryGiven = requested.stream().anyMatch(name -> name.getName().strip().equals(place.getName()));
        if (!isPrimaryGiven) {
            wanted.add(primary);
        }
        place.getNames().removeIf(name -> !wanted.contains(key(name.getLanguageCode(), name.getName())));
        for (PlaceNameRequest name : requested) {
            addName(place, name.getLanguageCode(), name.getName().strip(), Boolean.TRUE.equals(name.getIsAlias()));
        }
        if (!isPrimaryGiven) {
            addName(place, PlaceName.ANY_LANGUAGE, place.getName(), false);
        }
    }

    private static void addName(Place place, String languageCode, String name, boolean isAlias) {
        for (PlaceName existing : place.getNames()) {
            if (existing.getLanguageCode().equals(languageCode) && existing.getName().equals(name)) {
                existing.setAlias(existing.isAlias() && isAlias);
                return;
            }
        }
        PlaceName added = new PlaceName();
        added.setPlace(place);
        added.setLanguageCode(languageCode);
        added.setName(name);
        added.setAlias(isAlias);
        place.getNames().add(added);
    }

    private static String key(String languageCode, String name) {
        return languageCode + "\u0000" + name;
    }

    private Set<Category> categoriesFor(List<UUID> ids) {
        Set<Category> categories = new HashSet<>();
        for (UUID categoryId : new HashSet<>(ids)) {
            categories.add(categoryRepository.findByIdAndDeletedAtIsNull(categoryId)
                    .orElseThrow(() -> new IllegalStateException("Category with id=%s does not exist"
                            .formatted(categoryId))));
        }
        return categories;
    }

    private Place parentFor(UUID parentId, String countryCode) {
        Place parent = placeRepository.findByIdAndDeletedAtIsNull(parentId)
                .orElseThrow(() -> new IllegalStateException("Parent place with id=%s does not exist"
                        .formatted(parentId)));
        if (parent.getStatus() == PlaceStatus.MERGED) {
            throw new IllegalStateException("Parent place with id=%s was merged into another".formatted(parentId));
        }
        if (!Objects.equals(parent.getCountryCode(), countryCode)) {
            throw new IllegalStateException("The parent place is in another country");
        }
        return parent;
    }

    private String requireFreeSlug(String slug, UUID id) {
        boolean isTaken = id == null
                ? placeRepository.existsBySlug(slug)
                : placeRepository.existsBySlugAndIdNot(slug, id);
        if (isTaken) {
            throw new IllegalStateException("Slug %s is taken".formatted(slug));
        }
        return slug;
    }

    private String freeSlug(String base) {
        String slug = base;
        for (int suffix = 2; placeRepository.existsBySlug(slug); suffix++) {
            slug = base + "-" + suffix;
        }
        return slug;
    }

    private static void requireNotMerged(Place place) {
        if (place.getStatus() == PlaceStatus.MERGED) {
            throw new IllegalStateException("Place with id=%s was merged into %s".formatted(place.getId(),
                    place.getMergedIntoId()));
        }
    }

    private Place lock(UUID id) {
        return placeRepository.lockById(id).orElseThrow(() -> PlaceQueryService.notFound(id));
    }
}
