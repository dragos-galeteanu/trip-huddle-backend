package com.triphuddle.api.user.controller;

import com.triphuddle.common.controller.BaseJpaController;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.service.BaseJpaService;
import com.triphuddle.api.user.dto.CreateUserProfileRequest;
import com.triphuddle.api.user.dto.UpdateUserProfileRequest;
import com.triphuddle.api.user.dto.UserProfileResponse;
import com.triphuddle.api.user.entity.UserProfile;
import com.triphuddle.api.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/user-profiles")
@RequiredArgsConstructor
@Tag(name = "User profiles")
public class UserProfileController extends BaseJpaController<UserProfile, CreateUserProfileRequest, UpdateUserProfileRequest, UserProfileResponse> {

    private final UserProfileService userProfileService;

    @Override
    protected BaseJpaService<UserProfile, CreateUserProfileRequest, UpdateUserProfileRequest, UserProfileResponse> getService() {
        return userProfileService;
    }

    @Override
    @PostMapping
    @PreAuthorize("@UserProfilePolicy.canCreate(authentication, #createUserProfileRequest)")
    public ResponseEntity<UserProfileResponse> create(@Valid @RequestBody CreateUserProfileRequest createUserProfileRequest) {
        return super.create(createUserProfileRequest);
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("@UserProfilePolicy.canGetById(authentication, #id)")
    public ResponseEntity<UserProfileResponse> getById(@PathVariable UUID id) {
        return super.getById(id);
    }

    @Override
    @GetMapping
    @PreAuthorize("@UserProfilePolicy.canGetAll(authentication)")
    public ResponseEntity<Paginated<UserProfileResponse>> getAll(@Valid @ModelAttribute PaginationFilter filter) {
        return super.getAll(filter);
    }

    @Override
    @PutMapping
    @PreAuthorize("@UserProfilePolicy.canUpdate(authentication, #updateUserProfileRequest)")
    public ResponseEntity<UserProfileResponse> update(@Valid @RequestBody UpdateUserProfileRequest updateUserProfileRequest) {
        return super.update(updateUserProfileRequest);
    }

    @Override
    @DeleteMapping("/{id}/disable")
    @PreAuthorize("@UserProfilePolicy.canDisable(authentication)")
    public ResponseEntity<UserProfileResponse> disable(@PathVariable UUID id) {
        return super.disable(id);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("@UserProfilePolicy.canDelete(authentication, #id)")
    public ResponseEntity<UserProfileResponse> delete(@PathVariable UUID id) {
        return super.delete(id);
    }
}
