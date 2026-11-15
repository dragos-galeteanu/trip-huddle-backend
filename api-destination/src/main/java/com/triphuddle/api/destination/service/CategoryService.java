package com.triphuddle.api.destination.service;

import com.triphuddle.api.destination.dto.CategoryRequest;
import com.triphuddle.api.destination.dto.CategoryResponse;
import com.triphuddle.api.destination.entity.Category;
import com.triphuddle.api.destination.repository.CategoryRepository;
import com.triphuddle.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The staff-managed category tree: read by everyone with access, changed by staff.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * Get the whole tree, parents before children and siblings in order.
     *
     * @return the categories
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getTree() {
        return categoryRepository.findTree().stream().map(CategoryService::toResponse).toList();
    }

    /**
     * Create a category, at the top or under a parent.
     *
     * @param request the category
     * @return the created category
     */
    public CategoryResponse create(CategoryRequest request) {
        requireFreeSlug(request.getSlug());
        Category parent = request.getParentId() == null ? null : find(request.getParentId());
        Category category = new Category();
        category.setParent(parent);
        apply(category, request);
        Category saved = categoryRepository.save(category);
        saved.setPath((parent == null ? "/" : parent.getPath()) + saved.getId() + "/");
        saved.setDepth(parent == null ? 0 : parent.getDepth() + 1);
        log.info("Category created [id={}, slug={}]", saved.getId(), saved.getSlug());
        return toResponse(saved);
    }

    /**
     * Change a category; a new parent moves it with its subtree.
     *
     * @param id the ID of the category
     * @param request the new details
     * @return the category
     */
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = find(id);
        if (!category.getSlug().equals(request.getSlug())) {
            requireFreeSlug(request.getSlug());
        }
        apply(category, request);
        UUID currentParentId = category.getParent() == null ? null : category.getParent().getId();
        if (!Objects.equals(currentParentId, request.getParentId())) {
            Category parent = request.getParentId() == null ? null : find(request.getParentId());
            if (parent != null && parent.getPath().startsWith(category.getPath())) {
                throw new IllegalStateException("A category can't be moved into itself or one inside it");
            }
            String oldPrefix = category.getPath();
            String newPrefix = (parent == null ? "/" : parent.getPath()) + id + "/";
            int depthDelta = (parent == null ? 0 : parent.getDepth() + 1) - category.getDepth();
            category.setParent(parent);
            categoryRepository.saveAndFlush(category);
            categoryRepository.movePaths(oldPrefix, newPrefix, depthDelta);
            category = find(id);
        }
        log.info("Category updated [id={}]", id);
        return toResponse(category);
    }

    /**
     * Delete a category with no subcategories; places lose it.
     *
     * @param id the ID of the category
     */
    public void delete(UUID id) {
        Category category = find(id);
        if (categoryRepository.existsByParentIdAndDeletedAtIsNull(id)) {
            throw new IllegalStateException("Category with id=%s has subcategories; move or delete them first"
                    .formatted(id));
        }
        category.setDeletedAt(Instant.now());
        categoryRepository.saveAndFlush(category);
        int unlinked = categoryRepository.unlinkPlaces(id);
        log.info("Category deleted [id={}, placesUnlinked={}]", id, unlinked);
    }

    private static void apply(Category category, CategoryRequest request) {
        category.setSlug(request.getSlug());
        category.setName(request.getName().strip());
        category.setDescription(request.getDescription() == null || request.getDescription().isBlank()
                ? null : request.getDescription().strip());
        category.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        category.getWikidataClassIds().clear();
        category.getWikidataClassIds().addAll(request.getWikidataClassIds());
    }

    private void requireFreeSlug(String slug) {
        if (categoryRepository.existsBySlugAndDeletedAtIsNull(slug)) {
            throw new IllegalStateException("Category slug %s is taken".formatted(slug));
        }
    }

    private Category find(UUID id) {
        return categoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category with id=%s not found".formatted(id)));
    }

    private static CategoryResponse toResponse(Category category) {
        UUID parentId = category.getParent() == null ? null : category.getParent().getId();
        return new CategoryResponse(category.getId(), parentId, category.getSlug(), category.getName(),
                category.getDescription(), category.getSortOrder(), category.getDepth(),
                category.getWikidataClassIds().stream().sorted().toList());
    }
}
