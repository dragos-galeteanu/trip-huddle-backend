package com.triphuddle.api.destination.dto;

import java.util.UUID;

/**
 * A short reference to a category.
 *
 * @param id the ID
 * @param slug the slug
 * @param name the name
 */
public record CategoryRef(
        UUID id,
        String slug,
        String name
) {
}
