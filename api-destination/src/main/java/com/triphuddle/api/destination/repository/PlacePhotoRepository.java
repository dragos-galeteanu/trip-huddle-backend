package com.triphuddle.api.destination.repository;

import com.triphuddle.api.destination.entity.PlacePhoto;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Data access for {@link PlacePhoto}.
 */
@Repository
public interface PlacePhotoRepository extends BaseJpaRepository<PlacePhoto, UUID> {

    /**
     * Get a place's photos, the primary one first.
     *
     * @param placeId the ID of the place
     * @return the photos that are not deleted
     */
    @Query("""
            select p from PlacePhoto p where p.place.id = :placeId and p.deletedAt is null
            order by p.isPrimary desc, p.createdAt
            """)
    List<PlacePhoto> findForPlace(@Param("placeId") UUID placeId);

    /**
     * Get the primary photos of some places.
     *
     * @param placeIds the IDs of the places
     * @return their primary photos
     */
    @Query("select p from PlacePhoto p where p.place.id in :placeIds and p.isPrimary = true and p.deletedAt is null")
    List<PlacePhoto> findPrimaryForPlaces(@Param("placeIds") Collection<UUID> placeIds);

    /**
     * Check if a place already has a file, deleted or not, so an import doesn't add back what staff removed.
     *
     * @param placeId the ID of the place
     * @param sourceFileName the file's name
     * @return true if the place has it
     */
    @Query("select count(p) > 0 from PlacePhoto p where p.place.id = :placeId and p.sourceFileName = :fileName")
    boolean hasFile(@Param("placeId") UUID placeId, @Param("fileName") String sourceFileName);

    /**
     * Check if a place has a primary photo.
     *
     * @param placeId the ID of the place
     * @return true if it has one that is not deleted
     */
    @Query("select count(p) > 0 from PlacePhoto p where p.place.id = :placeId and p.isPrimary = true "
            + "and p.deletedAt is null")
    boolean hasPrimary(@Param("placeId") UUID placeId);
}
