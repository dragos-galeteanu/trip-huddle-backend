package com.triphuddle.api.chat.dto;

import com.triphuddle.api.chat.entity.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request body for opening a support ticket with its first message; staff set {@code userId} to open one towards a user.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class CreateSupportTicketRequest {

    // Only staff may set it; a customer opens tickets for themselves.
    private UUID userId;

    @NotBlank(message = "Subject is required")
    @Size(max = 200, message = "Subject cannot exceed 200 characters")
    private String subject;

    @NotNull(message = "Category is required")
    private TicketCategory category;

    // Retrying with the same clientMessageId returns the ticket already opened.
    @NotBlank(message = "Client message ID is required")
    @Size(max = 64, message = "Client message ID cannot exceed 64 characters")
    private String clientMessageId;

    @NotBlank(message = "Message is required")
    @Size(max = 4000, message = "Message cannot exceed 4000 characters")
    private String body;

    private UUID groupId;

    private UUID conversationId;

    private String messageId;

    private UUID messageReportId;
}
