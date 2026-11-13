package com.triphuddle.api.group.controller;

import com.triphuddle.api.group.dto.CreateGroupMembershipRequest;
import com.triphuddle.api.group.dto.GroupMembershipResponse;
import com.triphuddle.api.group.dto.UpdateGroupMembershipRequest;
import com.triphuddle.api.group.entity.GroupMembership;
import com.triphuddle.api.group.service.GroupMembershipService;
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
 * REST endpoints for group memberships under {@code /api/v1/groups/memberships}.
 */
@RestController
@RequestMapping("/api/v1/groups/memberships")
@RequiredArgsConstructor
@Tag(name = "Group memberships")
public class GroupMembershipController extends BaseJpaController<GroupMembership, CreateGroupMembershipRequest, UpdateGroupMembershipRequest, GroupMembershipResponse> {

    private final GroupMembershipService groupMembershipService;

    @Override
    protected BaseJpaService<GroupMembership, CreateGroupMembershipRequest, UpdateGroupMembershipRequest, GroupMembershipResponse> getService() {
        return groupMembershipService;
    }

    @Override
    @PostMapping
    @PreAuthorize("@GroupMembershipPolicy.canCreate(authentication, #createGroupMembershipRequest)")
    public ResponseEntity<GroupMembershipResponse> create(@Valid @RequestBody CreateGroupMembershipRequest createGroupMembershipRequest) {
        return super.create(createGroupMembershipRequest);
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("@GroupMembershipPolicy.canGetById(authentication, #id)")
    public ResponseEntity<GroupMembershipResponse> getById(@PathVariable UUID id) {
        return super.getById(id);
    }

    @Override
    @GetMapping
    @PreAuthorize("@GroupMembershipPolicy.canGetAll(authentication)")
    public ResponseEntity<Paginated<GroupMembershipResponse>> getAll(@Valid @ModelAttribute PaginationFilter filter) {
        return super.getAll(filter);
    }

    /**
     * Get the memberships of a group, with pagination.
     *
     * @param groupId the ID of the group
     * @param filter pagination filter
     * @return a paginated response object containing the memberships of the group
     */
    @GetMapping("/group/{groupId}")
    @Operation(summary = "Get the memberships of a group")
    @PreAuthorize("@GroupMembershipPolicy.canGetAllByGroup(authentication, #groupId)")
    public ResponseEntity<Paginated<GroupMembershipResponse>> getAllByGroup(
            @PathVariable UUID groupId, @Valid @ModelAttribute PaginationFilter filter) {
        return ResponseEntity.ok(groupMembershipService.getAllByGroup(groupId, filter));
    }

    @Override
    @PutMapping
    @PreAuthorize("@GroupMembershipPolicy.canUpdate(authentication, #updateGroupMembershipRequest)")
    public ResponseEntity<GroupMembershipResponse> update(@Valid @RequestBody UpdateGroupMembershipRequest updateGroupMembershipRequest) {
        return super.update(updateGroupMembershipRequest);
    }

    @Override
    @DeleteMapping("/{id}/disable")
    @PreAuthorize("@GroupMembershipPolicy.canDisable(authentication)")
    public ResponseEntity<GroupMembershipResponse> disable(@PathVariable UUID id) {
        return super.disable(id);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("@GroupMembershipPolicy.canDelete(authentication, #id)")
    public ResponseEntity<GroupMembershipResponse> delete(@PathVariable UUID id) {
        return super.delete(id);
    }
}
