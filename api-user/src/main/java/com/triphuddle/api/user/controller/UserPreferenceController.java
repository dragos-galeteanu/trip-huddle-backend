package com.triphuddle.api.user.controller;

import com.triphuddle.common.controller.BaseMongoController;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.service.BaseMongoService;
import com.triphuddle.api.user.dto.CreateUserPreferenceRequest;
import com.triphuddle.api.user.dto.UpdateUserPreferenceRequest;
import com.triphuddle.api.user.dto.UserPreferenceResponse;
import com.triphuddle.api.user.entity.UserPreference;
import com.triphuddle.api.user.service.UserPreferenceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for user preferences under {@code /api/v1/users/user-preferences}.
 */
@RestController
@RequestMapping("/api/v1/users/user-preferences")
@RequiredArgsConstructor
@Tag(name = "User preferences")
public class UserPreferenceController extends BaseMongoController<UserPreference, CreateUserPreferenceRequest,
        UpdateUserPreferenceRequest, UserPreferenceResponse> {

    private final UserPreferenceService userPreferenceService;

    @Override
    protected BaseMongoService<UserPreference, CreateUserPreferenceRequest,
                    UpdateUserPreferenceRequest, UserPreferenceResponse> getService() {
        return userPreferenceService;
    }

    @Override
    @PostMapping
    @PreAuthorize("@UserPreferencePolicy.canCreate(authentication, #createUserPreferenceRequest)")
    public ResponseEntity<UserPreferenceResponse> create(
            @Valid @RequestBody CreateUserPreferenceRequest createUserPreferenceRequest) {
        return super.create(createUserPreferenceRequest);
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("@UserPreferencePolicy.canGetById(authentication, #id)")
    public ResponseEntity<UserPreferenceResponse> getById(@PathVariable String id) {
        return super.getById(id);
    }

    @Override
    @GetMapping
    @PreAuthorize("@UserPreferencePolicy.canGetAll(authentication)")
    public ResponseEntity<Paginated<UserPreferenceResponse>> getAll(@Valid @ModelAttribute PaginationFilter filter) {
        return super.getAll(filter);
    }

    @Override
    @PutMapping
    @PreAuthorize("@UserPreferencePolicy.canUpdate(authentication, #updateUserPreferenceRequest)")
    public ResponseEntity<UserPreferenceResponse> update(
            @Valid @RequestBody UpdateUserPreferenceRequest updateUserPreferenceRequest) {
        return super.update(updateUserPreferenceRequest);
    }

    @Override
    @DeleteMapping("/{id}/disable")
    @PreAuthorize("@UserPreferencePolicy.canDisable(authentication)")
    public ResponseEntity<UserPreferenceResponse> disable(@PathVariable String id) {
        return super.disable(id);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("@UserPreferencePolicy.canDelete(authentication, #id)")
    public ResponseEntity<UserPreferenceResponse> delete(@PathVariable String id) {
        return super.delete(id);
    }
}
