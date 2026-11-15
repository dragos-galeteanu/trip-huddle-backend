package com.triphuddle.api.chat.repository;

import com.triphuddle.api.chat.entity.Conversation;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link Conversation}.
 */
@Repository
public interface ConversationRepository extends BaseJpaRepository<Conversation, UUID> {

    /**
     * Find the chat of a group.
     *
     * @param groupId the ID of the group
     * @return the conversation, or empty if the group has none yet
     */
    Optional<Conversation> findByGroupId(UUID groupId);

    /**
     * Find the private chat of a pair of users.
     *
     * @param privateKey the sorted, joined IDs of the two users
     * @return the conversation, or empty if the pair has none yet
     */
    Optional<Conversation> findByPrivateKey(String privateKey);
}
