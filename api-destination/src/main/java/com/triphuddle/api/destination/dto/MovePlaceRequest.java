package com.triphuddle.api.destination.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Where to move a place, with everything in it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovePlaceRequest {

    /**
     * The new parent, in the same country, or null for the top of the country.
     */
    private UUID parentId;
}
