package com.triphuddle.api.destination.controller;

import com.triphuddle.api.destination.dto.CategoryRequest;
import com.triphuddle.api.destination.dto.CategoryResponse;
import com.triphuddle.api.destination.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The category tree under {@code /api/v1/destinations/categories}.
 */
@RestController
@RequestMapping("/api/v1/destinations/categories")
@RequiredArgsConstructor
@Tag(name = "Categories")
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Get the whole tree, parents before children.
     *
     * @param groupId the group the caller acts for, or null
     * @return the categories
     */
    @GetMapping
    @Operation(summary = "Get the category tree")
    @PreAuthorize("@DestinationPolicy.canBrowse(authentication, #groupId)")
    public ResponseEntity<List<CategoryResponse>> getTree(@RequestParam(required = false) UUID groupId) {
        return ResponseEntity.ok(categoryService.getTree());
    }

    /**
     * Create a category.
     *
     * @param request the category
     * @return the created category
     */
    @PostMapping
    @Operation(summary = "Create a category")
    @PreAuthorize("@DestinationPolicy.canManage(authentication)")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return new ResponseEntity<>(categoryService.create(request), HttpStatus.CREATED);
    }

    /**
     * Change or move a category.
     *
     * @param id the ID of the category
     * @param request the new details
     * @return the category
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update a category")
    @PreAuthorize("@DestinationPolicy.canManage(authentication)")
    public ResponseEntity<CategoryResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.update(id, request));
    }

    /**
     * Delete a category with no subcategories.
     *
     * @param id the ID of the category
     * @return no content
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a category")
    @PreAuthorize("@DestinationPolicy.canManage(authentication)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
