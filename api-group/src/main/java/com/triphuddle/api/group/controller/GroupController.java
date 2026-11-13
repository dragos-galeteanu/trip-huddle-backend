package com.triphuddle.api.group.controller;

import com.triphuddle.api.group.dto.CreateGroupRequest;
import com.triphuddle.api.group.dto.GroupResponse;
import com.triphuddle.api.group.dto.UpdateGroupRequest;
import com.triphuddle.api.group.entity.Group;
import com.triphuddle.api.group.service.GroupService;
import com.triphuddle.common.controller.BaseJpaController;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.service.BaseJpaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST endpoints for groups under {@code /api/v1/groups}.
 */
@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
@Tag(name = "Groups")
public class GroupController extends BaseJpaController<Group, CreateGroupRequest, UpdateGroupRequest, GroupResponse> {

    private final GroupService groupService;

    @Override
    protected BaseJpaService<Group, CreateGroupRequest, UpdateGroupRequest, GroupResponse> getService() {
        return groupService;
    }

    @Override
    @PostMapping
    @PreAuthorize("@GroupPolicy.canCreate(authentication, #createGroupRequest)")
    public ResponseEntity<GroupResponse> create(@Valid @RequestBody CreateGroupRequest createGroupRequest) {
        return super.create(createGroupRequest);
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("@GroupPolicy.canGetById(authentication, #id)")
    public ResponseEntity<GroupResponse> getById(@PathVariable UUID id) {
        return super.getById(id);
    }

    @Override
    @GetMapping
    @PreAuthorize("@GroupPolicy.canGetAll(authentication)")
    public ResponseEntity<Paginated<GroupResponse>> getAll(@Valid @ModelAttribute PaginationFilter filter) {
        return super.getAll(filter);
    }

    /**
     * Get the groups the signed-in user belongs to, with pagination.
     *
     * @param filter pagination filter
     * @return a paginated response object containing the user's groups
     */
    @GetMapping("/mine")
    @Operation(summary = "Get the groups the current user is a member of")
    @PreAuthorize("@GroupPolicy.canGetMine(authentication)")
    public ResponseEntity<Paginated<GroupResponse>> getMine(@Valid @ModelAttribute PaginationFilter filter) {
        return ResponseEntity.ok(groupService.getMine(filter));
    }

    @Override
    @PutMapping
    @PreAuthorize("@GroupPolicy.canUpdate(authentication, #updateGroupRequest)")
    public ResponseEntity<GroupResponse> update(@Valid @RequestBody UpdateGroupRequest updateGroupRequest) {
        return super.update(updateGroupRequest);
    }

    @Override
    @DeleteMapping("/{id}/disable")
    @PreAuthorize("@GroupPolicy.canDisable(authentication)")
    public ResponseEntity<GroupResponse> disable(@PathVariable UUID id) {
        return super.disable(id);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("@GroupPolicy.canDelete(authentication, #id)")
    public ResponseEntity<GroupResponse> delete(@PathVariable UUID id) {
        return super.delete(id);
    }
}
