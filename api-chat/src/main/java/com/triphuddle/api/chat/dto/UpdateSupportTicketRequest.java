package com.triphuddle.api.chat.dto;

import com.triphuddle.api.chat.entity.TicketCategory;
import com.triphuddle.api.chat.entity.TicketPriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for staff changing a ticket's priority or category; a missing field stays as it is.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class UpdateSupportTicketRequest {

    private TicketPriority priority;

    private TicketCategory category;
}
