package com.triphuddle.api.chat.dto;

import com.triphuddle.common.filter.CursorPaginationFilter;
import com.triphuddle.api.chat.entity.TicketCategory;
import com.triphuddle.api.chat.entity.TicketPriority;
import com.triphuddle.api.chat.entity.TicketStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * The cursor, page size and filters of the staff ticket queue; a missing filter keeps every ticket.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SupportTicketQueueFilter extends CursorPaginationFilter {

    private TicketStatus status;

    private TicketCategory category;

    private TicketPriority priority;

    private UUID assigneeId;

    private Boolean isUnassigned = false;
}
