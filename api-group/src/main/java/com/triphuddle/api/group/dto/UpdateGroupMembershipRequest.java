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
public class UpdateGroupMembershipRequest {

    @NotNull(message = "ID is required")
    private UUID id;

    @NotNull(message = "Role is required")
    private GroupRole role;
}
