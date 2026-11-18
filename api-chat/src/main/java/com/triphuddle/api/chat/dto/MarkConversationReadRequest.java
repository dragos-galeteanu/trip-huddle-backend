package com.triphuddle.api.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for marking a conversation read up to and including a message.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class MarkConversationReadRequest {

    @NotBlank(message = "Message ID is required")
    private String messageId;
}
