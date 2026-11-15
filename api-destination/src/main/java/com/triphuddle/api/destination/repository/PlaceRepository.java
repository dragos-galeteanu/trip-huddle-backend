package com.triphuddle.api.destination.repository;

import com.triphuddle.api.destination.entity.Place;
import com.triphuddle.common.repository.BaseJpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link Place}; search is in {@link PlaceSearchRepository}.
 */
@Repository
public interface PlaceRepository extends BaseJpaRepository<Place, UUID> {

    /**
     * Lock a place, so moves and merges of it and its subtree happen one at a time.
     *
     * @param id the ID of the place
     * @return the locked place
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Place p where p.id = :id and p.deletedAt is null")
    Optional<Place> lockById(@Param("id") UUID id);

    /**
     * Find places by their IDs, with their parents.
     *
     * @param ids the IDs
     * @return the places, in no order
     */
    @Query("select distinct p from Place p left join fetch p.parent where p.id in :ids")
    List<Place> findAllWithParent(@Param("ids") Collection<UUID> ids);

    /**
     * Check if a slug is taken.
     *
     * @param slug the slug
     * @return true if a place has it
     */
    boolean existsBySlug(String slug);

    /**
     * Check if a slug is taken by another place, deleted ones included, as slugs are never reused.
     *
     * @param slug the slug
     * @param id the place that may keep it
     * @return true if another place has it
     */
    boolean existsBySlugAndIdNot(String slug, UUID id);

    boolean existsByWikidataId(String wikidataId);

    boolean existsByWikidataIdAndIdNot(String wikidataId, UUID id);

    boolean existsByOsmId(String osmId);

    boolean existsByOsmIdAndIdNot(String osmId, UUID id);

    /**
     * Check if a Wikidata ID belongs to another place.
     *
     * @param wikidataId the Wikidata ID
     * @param id the place that may have it, or null for none
     * @return true if another place has it
     */
    default boolean isWikidataIdTaken(String wikidataId, UUID id) {
        // Two queries rather than "(:id is null or ...)", whose null Postgres can't give a type.
        return id == null ? existsByWikidataId(wikidataId) : existsByWikidataIdAndIdNot(wikidataId, id);
    }

    /**
     * Check if an OpenStreetMap ID belongs to another place.
     *
     * @param osmId the OpenStreetMap ID
     * @param id the place that may have it, or null for none
     * @return true if another place has it
     */
    default boolean isOsmIdTaken(String osmId, UUID id) {
        return id == null ? existsByOsmId(osmId) : existsByOsmIdAndIdNot(osmId, id);
    }

    /**
     * Find a place by its Wikidata ID, deleted ones included, so an import doesn't bring back what staff deleted.
     *
     * @param wikidataId the Wikidata ID
     * @return the place
     */
    Optional<Place> findByWikidataId(String wikidataId);

    /**
     * Find a place by its slug.
     *
     * @param slug the slug
     * @return the place
     */
    Optional<Place> findBySlugAndDeletedAtIsNull(String slug);

    /**
     * Check if a place has children that are not deleted.
     *
     * @param parentId the ID of the place
     * @return true if it has any
     */
    boolean existsByParentIdAndDeletedAtIsNull(UUID parentId);

    /**
     * Rewrite the paths of a subtree after its root moved, and shift its depths.
     *
     * @param oldPrefix the root's old path
     * @param newPrefix the root's new path
     * @param depthDelta how much deeper the subtree now sits
     * @return how many places changed, the root included
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update destination_places
            set path = :newPrefix || substring(path from length(:oldPrefix) + 1),
                depth = depth + :depthDelta,
                updated_at = now()
            where path like :oldPrefix || '%'
            """, nativeQuery = true)
    int movePaths(@Param("oldPrefix") String oldPrefix, @Param("newPrefix") String newPrefix,
                  @Param("depthDelta") int depthDelta);

    /**
     * Move a place's direct children under another place; their paths are rewritten separately.
     *
     * @param fromParentId the old parent
     * @param toParentId the new parent
     * @return how many children moved
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update destination_places set parent_id = :toParentId, updated_at = now() "
            + "where parent_id = :fromParentId", nativeQuery = true)
    int reparentChildren(@Param("fromParentId") UUID fromParentId, @Param("toParentId") UUID toParentId);
}
