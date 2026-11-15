package com.triphuddle.api.chat.repository;

import com.triphuddle.api.chat.entity.UserBlock;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link UserBlock}; a block is in force while it is not deleted.
 */
@Repository
public interface UserBlockRepository extends BaseJpaRepository<UserBlock, UUID> {

    /**
     * Find the block one user placed on another, in force or lifted.
     *
     * @param blockerId the ID of the user who blocks
     * @param blockedId the ID of the user who is blocked
     * @return the block, or empty if there never was one
     */
    Optional<UserBlock> findByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    /**
     * Check if either of two users blocks the other.
     *
     * @param userId one user
     * @param otherUserId the other user
     * @return true if a block is in force in either direction
     */
    @Query("""
            select count(b) > 0 from UserBlock b
            where b.deletedAt is null
              and ((b.blockerId = :userId and b.blockedId = :otherUserId)
                or (b.blockerId = :otherUserId and b.blockedId = :userId))
            """)
    boolean existsBetween(@Param("userId") UUID userId, @Param("otherUserId") UUID otherUserId);

    /**
     * Find the users among the given ones who block a user.
     *
     * @param blockedId the ID of the blocked user
     * @param blockerIds the IDs of the users who may block them
     * @return the blocks in force from those users
     */
    List<UserBlock> findByBlockedIdAndBlockerIdInAndDeletedAtIsNull(UUID blockedId, Collection<UUID> blockerIds);

    /**
     * Find the blocks a user has in force.
     *
     * @param blockerId the ID of the user who blocks
     * @param pageable the page to return
     * @return a page of the user's blocks
     */
    Page<UserBlock> findByBlockerIdAndDeletedAtIsNull(UUID blockerId, Pageable pageable);
}
