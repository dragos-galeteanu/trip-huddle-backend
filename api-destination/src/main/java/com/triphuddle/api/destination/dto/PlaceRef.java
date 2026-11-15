package com.triphuddle.api.destination.dto;

import com.triphuddle.api.destination.entity.PlaceKind;
import java.util.UUID;

/**
 * A short reference to a place, e.g. in a breadcrumb.
 *
 * @param id the ID
 * @param slug the slug
 * @param name the name
 * @param kind the kind
 */
public record PlaceRef(
        UUID id,
        String slug,
        String name,
        PlaceKind kind
) {
}
