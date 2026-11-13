package com.triphuddle.api.group.controller;

import com.triphuddle.api.group.dto.CreateGroupInvitationRequest;
import com.triphuddle.api.group.dto.GroupInvitationTokenRequest;
import com.triphuddle.api.group.dto.GroupInvitationResponse;
import com.triphuddle.api.group.dto.UpdateGroupInvitationRequest;
import com.triphuddle.api.group.entity.GroupInvitation;
import com.triphuddle.api.group.service.GroupInvitationService;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups/invitations")
@RequiredArgsConstructor
@Tag(name = "Group invitations")
public class GroupInvitationController extends BaseJpaController<GroupInvitation, CreateGroupInvitationRequest, UpdateGroupInvitationRequest, GroupInvitationResponse> {

    private final GroupInvitationService groupInvitationService;

    @Override
    protected BaseJpaService<GroupInvitation, CreateGroupInvitationRequest, UpdateGroupInvitationRequest, GroupInvitationResponse> getService() {
        return groupInvitationService;
    }

    @Override
    @PostMapping
    @PreAuthorize("@GroupInvitationPolicy.canCreate(authentication, #createGroupInvitationRequest)")
    public ResponseEntity<GroupInvitationResponse> create(@Valid @RequestBody CreateGroupInvitationRequest createGroupInvitationRequest) {
        return super.create(createGroupInvitationRequest);
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("@GroupInvitationPolicy.canGetById(authentication, #id)")
    public ResponseEntity<GroupInvitationResponse> getById(@PathVariable UUID id) {
        return super.getById(id);
    }

    @Override
    @GetMapping
    @PreAuthorize("@GroupInvitationPolicy.canGetAll(authentication)")
    public ResponseEntity<Paginated<GroupInvitationResponse>> getAll(@Valid @ModelAttribute PaginationFilter filter) {
        return super.getAll(filter);
    }

    @GetMapping("/group/{groupId}")
    @Operation(summary = "Get the invitations of a group")
    @PreAuthorize("@GroupInvitationPolicy.canGetAllByGroup(authentication, #groupId)")
    public ResponseEntity<Paginated<GroupInvitationResponse>> getAllByGroup(
            @PathVariable UUID groupId, @Valid @ModelAttribute PaginationFilter filter) {
        return ResponseEntity.ok(groupInvitationService.getAllByGroup(groupId, filter));
    }

    @PostMapping("/accept")
    @Operation(summary = "Accept a group invitation by its token")
    public ResponseEntity<GroupInvitationResponse> accept(
            @Valid @RequestBody GroupInvitationTokenRequest groupInvitationTokenRequest, Authentication authentication) {
        return ResponseEntity.ok(groupInvitationService.accept(groupInvitationTokenRequest.getToken(), authentication));
    }

    @PostMapping("/reject")
    @Operation(summary = "Reject a group invitation by its token")
    public ResponseEntity<GroupInvitationResponse> reject(
            @Valid @RequestBody GroupInvitationTokenRequest groupInvitationTokenRequest) {
        return ResponseEntity.ok(groupInvitationService.reject(groupInvitationTokenRequest.getToken()));
    }

    @Override
    @PutMapping
    @PreAuthorize("@GroupInvitationPolicy.canUpdate(authentication, #updateGroupInvitationRequest)")
    public ResponseEntity<GroupInvitationResponse> update(@Valid @RequestBody UpdateGroupInvitationRequest updateGroupInvitationRequest) {
        return super.update(updateGroupInvitationRequest);
    }

    @Override
    @DeleteMapping("/{id}/disable")
    @PreAuthorize("@GroupInvitationPolicy.canDisable(authentication)")
    public ResponseEntity<GroupInvitationResponse> disable(@PathVariable UUID id) {
        return super.disable(id);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("@GroupInvitationPolicy.canDelete(authentication, #id)")
    public ResponseEntity<GroupInvitationResponse> delete(@PathVariable UUID id) {
        return super.delete(id);
    }
}
