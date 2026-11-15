package com.triphuddle.api.chat.repository;

import com.triphuddle.api.chat.entity.SupportTicket;
import com.triphuddle.api.chat.entity.TicketCategory;
import com.triphuddle.api.chat.entity.TicketPriority;
import com.triphuddle.api.chat.entity.TicketStatus;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link SupportTicket}.
 */
@Repository
public interface SupportTicketRepository extends BaseJpaRepository<SupportTicket, UUID> {

    /**
     * Find the ticket of a support conversation.
     *
     * @param conversationId the ID of the conversation
     * @return the ticket, or empty if the conversation is not a support chat
     */
    Optional<SupportTicket> findByConversationIdAndDeletedAtIsNull(UUID conversationId);

    /**
     * Find the tickets of the given conversations.
     *
     * @param conversationIds the IDs of the conversations
     * @return the tickets of those that are support chats
     */
    List<SupportTicket> findByConversationIdInAndDeletedAtIsNull(Collection<UUID> conversationIds);

    /**
     * Count the tickets a customer opened themselves that are in one of the statuses.
     *
     * @param requesterId the ID of the customer
     * @param statuses the statuses to count
     * @return the number of such tickets
     */
    @Query("""
            select count(t) from SupportTicket t
            where t.requesterId = :requesterId and t.openedByUserId = :requesterId
              and t.status in :statuses and t.deletedAt is null
            """)
    long countOpenedByRequesterInStatuses(@Param("requesterId") UUID requesterId,
                                          @Param("statuses") Collection<TicketStatus> statuses);

    /**
     * Find a customer's tickets, newest first, optionally with one status.
     *
     * @param requesterId the ID of the customer
     * @param status the status to keep, or null for all
     * @param pageable the page to return
     * @return a page of the tickets
     */
    @Query("""
            select t from SupportTicket t
            where t.requesterId = :requesterId and t.deletedAt is null
              and (:status is null or t.status = :status)
            order by t.createdAt desc
            """)
    Page<SupportTicket> findForRequester(@Param("requesterId") UUID requesterId,
                                         @Param("status") TicketStatus status,
                                         Pageable pageable);

    /**
     * Find the tickets of the staff queue, most urgent first and then oldest first.
     *
     * @param status the status to keep, or null for all
     * @param category the category to keep, or null for all
     * @param priority the priority to keep, or null for all
     * @param assigneeId the assignee to keep, or null for any
     * @param isUnassigned true to keep only tickets nobody has claimed
     * @param pageable the page to return
     * @return a page of the tickets
     */
    @Query(value = """
            select t from SupportTicket t
            where t.deletedAt is null
              and (:status is null or t.status = :status)
              and (:category is null or t.category = :category)
              and (:priority is null or t.priority = :priority)
              and (:assigneeId is null or t.assigneeId = :assigneeId)
              and (:isUnassigned = false or t.assigneeId is null)
            order by case t.priority
                         when com.triphuddle.api.chat.entity.TicketPriority.URGENT then 0
                         when com.triphuddle.api.chat.entity.TicketPriority.HIGH then 1
                         when com.triphuddle.api.chat.entity.TicketPriority.NORMAL then 2
                         else 3 end,
                     t.createdAt asc
            """,
            countQuery = """
            select count(t) from SupportTicket t
            where t.deletedAt is null
              and (:status is null or t.status = :status)
              and (:category is null or t.category = :category)
              and (:priority is null or t.priority = :priority)
              and (:assigneeId is null or t.assigneeId = :assigneeId)
              and (:isUnassigned = false or t.assigneeId is null)
            """)
    Page<SupportTicket> findQueue(@Param("status") TicketStatus status,
                                  @Param("category") TicketCategory category,
                                  @Param("priority") TicketPriority priority,
                                  @Param("assigneeId") UUID assigneeId,
                                  @Param("isUnassigned") boolean isUnassigned,
                                  Pageable pageable);

    /**
     * Lock the oldest tickets resolved before a moment, skipping those another instance has locked.
     *
     * @param cutoff the latest resolution time to close
     * @param pageable how many to take; its sort is ignored
     * @return the tickets due to close, locked until the transaction ends
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // -2 is SKIP LOCKED, so instances running the job at once split the work instead of waiting.
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select t from SupportTicket t
            where t.status = com.triphuddle.api.chat.entity.TicketStatus.RESOLVED
              and t.resolvedAt <= :cutoff and t.deletedAt is null
            order by t.resolvedAt asc
            """)
    List<SupportTicket> lockDueForAutoClose(@Param("cutoff") Instant cutoff, Pageable pageable);

    /**
     * Draw the next ticket number.
     *
     * @return the next value of {@code support_ticket_number_seq}
     */
    @Query(value = "select nextval('support_ticket_number_seq')", nativeQuery = true)
    long nextNumber();
}
