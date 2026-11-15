package com.triphuddle.api.chat.repository;

import com.triphuddle.api.chat.entity.SupportTicketEvent;
import com.triphuddle.common.repository.BaseJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Data access for {@link SupportTicketEvent}.
 */
@Repository
public interface SupportTicketEventRepository extends BaseJpaRepository<SupportTicketEvent, UUID> {

    /**
     * Find a ticket's history, oldest first.
     *
     * @param ticketId the ID of the ticket
     * @return the events of the ticket
     */
    List<SupportTicketEvent> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
