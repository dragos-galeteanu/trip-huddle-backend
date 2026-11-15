package com.triphuddle.api.destination.entity;

import com.triphuddle.common.entity.BaseJpaEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * A node of the staff-managed category tree, e.g. Heritage › Castle or Food › Restaurant › Seafood.
 */
@Entity
@Table(name = "destination_categories")
@Getter
@Setter
@NoArgsConstructor
public class Category extends BaseJpaEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    // Unique among categories that are not deleted, by a partial index.
    @Column(name = "slug", nullable = false, length = 64)
    private String slug;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    // The IDs from the top category down to this one, as /id/id/, so a subtree is a prefix match.
    @Column(name = "path", nullable = false, columnDefinition = "TEXT")
    private String path = "/";

    @Column(name = "depth", nullable = false)
    private int depth;

    // The Wikidata classes whose items the import files under this category, e.g. Q23413 for castle.
    @ElementCollection
    @CollectionTable(name = "destination_category_wikidata_classes", joinColumns = @JoinColumn(name = "category_id"))
    @Column(name = "class_id", nullable = false, length = 32)
    private Set<String> wikidataClassIds = new HashSet<>();
}
