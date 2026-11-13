package com.triphuddle.api.group.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request body for accepting or rejecting a group invitation by its token.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupInvitationTokenRequest {

    @NotNull(message = "Token is required")
    private UUID token;
}
