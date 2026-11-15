package com.triphuddle.api.chat.repository;

import com.triphuddle.api.chat.entity.ConversationParticipant;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link ConversationParticipant}; "active" means the participant has not left.
 */
@Repository
public interface ConversationParticipantRepository extends BaseJpaRepository<ConversationParticipant, UUID> {

    /**
     * Find a user's place in a conversation, whether they are active or have left.
     *
     * @param conversationId the ID of the conversation
     * @param userId the ID of the user
     * @return the participant, or empty if the user never took part
     */
    Optional<ConversationParticipant> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    /**
     * Find the active participants of a conversation.
     *
     * @param conversationId the ID of the conversation
     * @return the participants who have not left
     */
    List<ConversationParticipant> findByConversationIdAndLeftAtIsNull(UUID conversationId);

    /**
     * Find every participant, active or not, of the given conversations.
     *
     * @param conversationIds the IDs of the conversations
     * @return the participants of those conversations
     */
    List<ConversationParticipant> findByConversationIdIn(Collection<UUID> conversationIds);

    /**
     * Find the first page of a user's conversations, most recently active first.
     *
     * @param userId the ID of the user
     * @param pageable the size of the page; its sort is ignored
     * @return the user's participants with their conversations
     */
    @Query("""
            select p from ConversationParticipant p join fetch p.conversation c
            where p.userId = :userId and c.deletedAt is null
            order by p.lastActivityAt desc, p.id desc
            """)
    List<ConversationParticipant> findFirstPageForUser(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Find the page of a user's conversations after the cursor, most recently active first.
     *
     * @param userId the ID of the user
     * @param activityAt the activity time of the last item of the previous page
     * @param participantId the participant ID of the last item of the previous page
     * @param pageable the size of the page; its sort is ignored
     * @return the user's participants with their conversations
     */
    @Query("""
            select p from ConversationParticipant p join fetch p.conversation c
            where p.userId = :userId and c.deletedAt is null
              and (p.lastActivityAt < :activityAt or (p.lastActivityAt = :activityAt and p.id < :participantId))
            order by p.lastActivityAt desc, p.id desc
            """)
    List<ConversationParticipant> findPageForUserAfter(@Param("userId") UUID userId,
                                                       @Param("activityAt") Instant activityAt,
                                                       @Param("participantId") UUID participantId,
                                                       Pageable pageable);

    /**
     * Record a new message for every active participant: the sender's list moves, the others' unread count grows.
     *
     * @param conversationId the ID of the conversation
     * @param senderId the ID of the user who sent the message
     * @param activityAt the time of the message
     * @return the number of participants updated
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ConversationParticipant p
            set p.lastActivityAt = :activityAt,
                p.unreadCount = case when p.userId = :senderId then p.unreadCount else p.unreadCount + 1 end
            where p.conversation.id = :conversationId and p.leftAt is null
            """)
    int recordNewMessage(@Param("conversationId") UUID conversationId,
                         @Param("senderId") UUID senderId,
                         @Param("activityAt") Instant activityAt);

    /**
     * Mark every active participant of a group's chat as having left, as when the group is deleted.
     *
     * @param conversationId the ID of the conversation
     * @param leftAt the time they left
     * @return the number of participants updated
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ConversationParticipant p set p.leftAt = :leftAt
            where p.conversation.id = :conversationId and p.leftAt is null
            """)
    int markAllLeft(@Param("conversationId") UUID conversationId, @Param("leftAt") Instant leftAt);
}
