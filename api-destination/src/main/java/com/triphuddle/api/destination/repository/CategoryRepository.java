package com.triphuddle.api.destination.repository;

import com.triphuddle.api.destination.entity.Category;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Data access for {@link Category}.
 */
@Repository
public interface CategoryRepository extends BaseJpaRepository<Category, UUID> {

    /**
     * Get the whole tree, parents before children and siblings in order.
     *
     * @return the categories that are not deleted
     */
    @Query("select c from Category c where c.deletedAt is null order by c.depth, c.sortOrder, c.name")
    List<Category> findTree();

    /**
     * Check if a slug is taken by another category.
     *
     * @param slug the slug
     * @return true if one has it
     */
    boolean existsBySlugAndDeletedAtIsNull(String slug);

    /**
     * Check if a category has children that are not deleted.
     *
     * @param parentId the ID of the category
     * @return true if it has any
     */
    boolean existsByParentIdAndDeletedAtIsNull(UUID parentId);

    /**
     * Rewrite the paths of a subtree after its root moved, and shift its depths.
     *
     * @param oldPrefix the root's old path
     * @param newPrefix the root's new path
     * @param depthDelta how much deeper the subtree now sits
     * @return how many categories changed
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update destination_categories
            set path = :newPrefix || substring(path from length(:oldPrefix) + 1),
                depth = depth + :depthDelta,
                updated_at = now()
            where path like :oldPrefix || '%'
            """, nativeQuery = true)
    int movePaths(@Param("oldPrefix") String oldPrefix, @Param("newPrefix") String newPrefix,
                  @Param("depthDelta") int depthDelta);

    /**
     * Unlink a category from every place.
     *
     * @param categoryId the ID of the category
     * @return how many links were removed
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from destination_place_categories where category_id = :categoryId", nativeQuery = true)
    int unlinkPlaces(@Param("categoryId") UUID categoryId);
}
