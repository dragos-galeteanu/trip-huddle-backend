package com.triphuddle.api.chat.repository;

import com.triphuddle.api.chat.entity.ChatMessage;
import com.triphuddle.common.repository.BaseMongoRepository;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link ChatMessage}; the cursor-paged history queries live in {@code ChatMessageService}.
 */
@Repository
public interface ChatMessageRepository extends BaseMongoRepository<ChatMessage, ObjectId> {

    /**
     * Find the message a sender already sent with a client message ID.
     *
     * @param senderId the ID of the sender
     * @param clientMessageId the ID the client generated for the message
     * @return the message, or empty if none was sent with it
     */
    Optional<ChatMessage> findBySenderIdAndClientMessageId(UUID senderId, String clientMessageId);
}
