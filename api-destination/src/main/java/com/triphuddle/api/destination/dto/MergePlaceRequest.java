package com.triphuddle.api.destination.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * The place a duplicate is merged into.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MergePlaceRequest {

    @NotNull
    private UUID targetId;
}
