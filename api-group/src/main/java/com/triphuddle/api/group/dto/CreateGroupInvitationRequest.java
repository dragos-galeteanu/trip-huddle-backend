package com.triphuddle.api.group.dto;

import com.triphuddle.api.group.entity.GroupRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request body for creating a group invitation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateGroupInvitationRequest {

    @NotNull(message = "Group ID is required")
    private UUID groupId;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 255, message = "Email must be at most 255 characters")
    private String email;

    /** Role the invitee gets on joining; MEMBER when omitted. */
    private GroupRole role;
}
