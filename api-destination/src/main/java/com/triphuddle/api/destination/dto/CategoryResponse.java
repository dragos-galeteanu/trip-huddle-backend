package com.triphuddle.api.destination.dto;

import java.util.List;
import java.util.UUID;

/**
 * A category; the list of them is the tree, parents before children.
 *
 * @param id the ID
 * @param parentId its parent, or null at the top
 * @param slug the slug
 * @param name the name
 * @param description what it covers, or null
 * @param sortOrder its order among its siblings
 * @param depth how deep it is, 0 at the top
 * @param wikidataClassIds the Wikidata classes an import files under it
 */
public record CategoryResponse(
        UUID id,
        UUID parentId,
        String slug,
        String name,
        String description,
        int sortOrder,
        int depth,
        List<String> wikidataClassIds
) {
}
