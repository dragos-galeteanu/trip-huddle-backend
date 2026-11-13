package com.triphuddle.api.platform.controller;

import com.triphuddle.common.controller.BaseJpaController;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.service.BaseJpaService;
import com.triphuddle.api.platform.dto.CreateLanguageRequest;
import com.triphuddle.api.platform.dto.LanguageResponse;
import com.triphuddle.api.platform.dto.UpdateLanguageRequest;
import com.triphuddle.api.platform.entity.Language;
import com.triphuddle.api.platform.service.LanguageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST endpoints for languages under {@code /api/v1/platform/languages}; reads are public.
 */
@RestController
@RequestMapping("/api/v1/platform/languages")
@RequiredArgsConstructor
@Tag(name = "Languages")
public class LanguageController extends BaseJpaController<Language, CreateLanguageRequest, UpdateLanguageRequest, LanguageResponse> {

    private final LanguageService languageService;

    @Override
    protected BaseJpaService<Language, CreateLanguageRequest, UpdateLanguageRequest, LanguageResponse> getService() {
        return languageService;
    }

    @Override
    @PostMapping
    @PreAuthorize("@LanguagePolicy.canCreate(authentication, #createLanguageRequest)")
    public ResponseEntity<LanguageResponse> create(@Valid @RequestBody CreateLanguageRequest createLanguageRequest) {
        return super.create(createLanguageRequest);
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("@LanguagePolicy.canGetById(authentication, #id)")
    public ResponseEntity<LanguageResponse> getById(@PathVariable UUID id) {
        return super.getById(id);
    }

    @Override
    @GetMapping
    @PreAuthorize("@LanguagePolicy.canGetAll(authentication)")
    public ResponseEntity<Paginated<LanguageResponse>> getAll(@Valid @ModelAttribute PaginationFilter filter) {
        return super.getAll(filter);
    }

    @Override
    @PutMapping
    @PreAuthorize("@LanguagePolicy.canUpdate(authentication, #updateLanguageRequest)")
    public ResponseEntity<LanguageResponse> update(@Valid @RequestBody UpdateLanguageRequest updateLanguageRequest) {
        return super.update(updateLanguageRequest);
    }

    @Override
    @DeleteMapping("/{id}/disable")
    @PreAuthorize("@LanguagePolicy.canDisable(authentication)")
    public ResponseEntity<LanguageResponse> disable(@PathVariable UUID id) {
        return super.disable(id);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("@LanguagePolicy.canDelete(authentication, #id)")
    public ResponseEntity<LanguageResponse> delete(@PathVariable UUID id) {
        return super.delete(id);
    }
}
