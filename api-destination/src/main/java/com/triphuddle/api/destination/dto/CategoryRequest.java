package com.triphuddle.api.destination.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A category to create, or its new details; a new parent moves it with its subtree.
 */
@Data
@NoArgsConstructor
public class CategoryRequest {

    private UUID parentId;

    @NotBlank
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$")
    @Size(max = 64)
    private String slug;

    @NotBlank
    @Size(max = 120)
    private String name;

    @Size(max = 2000)
    private String description;

    private Integer sortOrder = 0;

    /**
     * The Wikidata classes whose notable items an import files under this category, e.g. Q23413 for castle.
     */
    @Size(max = 20)
    private List<@Pattern(regexp = "^Q[0-9]+$") String> wikidataClassIds = new ArrayList<>();
}
