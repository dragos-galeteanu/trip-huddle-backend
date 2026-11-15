package com.triphuddle.api.chat.repository;

import com.triphuddle.api.chat.entity.ChatUser;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link ChatUser}.
 */
@Repository
public interface ChatUserRepository extends BaseJpaRepository<ChatUser, UUID> {

    /**
     * Find a chat user by their platform user ID, whatever their state.
     *
     * @param userId the platform user ID
     * @return the chat user, or empty if api-user never reported them
     */
    Optional<ChatUser> findByUserId(UUID userId);

    /**
     * Check if a user is known and neither disabled nor deleted.
     *
     * @param userId the platform user ID
     * @return true if the user can take part in chats
     */
    boolean existsByUserIdAndDeletedAtIsNullAndDisabledAtIsNull(UUID userId);

    /**
     * Find which of the given users are disabled or deleted.
     *
     * @param userIds the platform user IDs
     * @return the chat users among them that are disabled or deleted
     */
    @Query("""
            select k from ChatUser k
            where k.userId in :userIds and (k.disabledAt is not null or k.deletedAt is not null)
            """)
    List<ChatUser> findInactiveAmong(Collection<UUID> userIds);
}
