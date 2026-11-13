package com.triphuddle.api.group.dto;

import com.triphuddle.api.group.entity.GroupRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request body for updating a group invitation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGroupInvitationRequest {

    @NotNull(message = "ID is required")
    private UUID id;

    @NotNull(message = "Role is required")
    private GroupRole role;
}
