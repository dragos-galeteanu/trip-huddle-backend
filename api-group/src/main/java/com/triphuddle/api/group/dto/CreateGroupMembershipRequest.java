package com.triphuddle.api.group.dto;

import com.triphuddle.api.group.entity.GroupRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateGroupMembershipRequest {

    @NotNull(message = "Group ID is required")
    private UUID groupId;

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Role is required")
    private GroupRole role;
}
