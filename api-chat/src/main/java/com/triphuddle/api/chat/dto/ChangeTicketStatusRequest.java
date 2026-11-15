package com.triphuddle.api.chat.dto;

import com.triphuddle.api.chat.entity.TicketStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for moving a ticket to another status; asking OPEN or IN_PROGRESS of a finished ticket reopens it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangeTicketStatusRequest {

    @NotNull(message = "Status is required")
    private TicketStatus status;
}
