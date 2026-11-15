package com.triphuddle.api.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for editing the text of a message.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EditMessageRequest {

    @NotBlank(message = "Message body is required")
    @Size(max = 4000, message = "Message cannot exceed 4000 characters")
    private String body;
}
