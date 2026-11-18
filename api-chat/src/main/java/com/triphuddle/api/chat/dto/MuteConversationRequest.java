package com.triphuddle.api.chat.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Request body for muting or unmuting a conversation; without {@code mutedUntil} a mute lasts until undone.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class MuteConversationRequest {

    @NotNull(message = "isMuted is required")
    private Boolean isMuted;

    @Future(message = "mutedUntil must be in the future")
    private Instant mutedUntil;
}
