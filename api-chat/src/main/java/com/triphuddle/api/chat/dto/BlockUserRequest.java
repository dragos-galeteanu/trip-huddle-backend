package com.triphuddle.api.chat.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request body for blocking a user.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlockUserRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;
}
