package com.triphuddle.api.group.service;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.group.GroupMembershipCreatedContract;
import com.triphuddle.amqp.group.GroupMembershipCreatedEvent;
import com.triphuddle.amqp.group.GroupMembershipDeletedContract;
import com.triphuddle.amqp.group.GroupMembershipDeletedEvent;
import com.triphuddle.amqp.group.GroupMembershipDisabledContract;
import com.triphuddle.amqp.group.GroupMembershipDisabledEvent;
import com.triphuddle.amqp.group.GroupMembershipUpdatedContract;
import com.triphuddle.amqp.group.GroupMembershipUpdatedEvent;
import com.triphuddle.api.group.dto.CreateGroupMembershipRequest;
import com.triphuddle.api.group.dto.GroupMembershipResponse;
import com.triphuddle.api.group.dto.UpdateGroupMembershipRequest;
import com.triphuddle.api.group.entity.Group;
import com.triphuddle.api.group.entity.GroupMembership;
import com.triphuddle.api.group.entity.GroupRole;
import com.triphuddle.api.group.mapper.GroupMembershipMapper;
import com.triphuddle.api.group.repository.GroupMembershipRepository;
import com.triphuddle.api.group.repository.GroupRepository;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.service.BaseJpaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Manages group memberships and keeps every group with at least one active Admin.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class GroupMembershipService implements BaseJpaService<GroupMembership, CreateGroupMembershipRequest,
        UpdateGroupMembershipRequest, GroupMembershipResponse> {

    private final GroupMembershipRepository groupMembershipRepository;
    private final GroupRepository groupRepository;
    private final GroupMembershipMapper groupMembershipMapper;
    private final RabbitMQMessageProducer messageProducer;

    @Override
    public GroupMembershipResponse create(CreateGroupMembershipRequest createGroupMembershipRequest) {
        UUID groupId = createGroupMembershipRequest.getGroupId();
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group with id=%s not found".formatted(groupId)));

        UUID userId = createGroupMembershipRequest.getUserId();
        GroupRole role = createGroupMembershipRequest.getRole();

        if (groupMembershipRepository.existsByGroupIdAndUserIdAndDeletedAtIsNull(group.getId(), userId)) {
            throw new IllegalStateException("GroupMembership with groupId=%s and userId=%s already exists"
                    .formatted(group.getId(), userId));
        }

        GroupMembership groupMembership = new GroupMembership();
        groupMembership.setGroup(group);
        groupMembership.setUserId(userId);
        groupMembership.setRole(role);

        GroupMembership saved = groupMembershipRepository.save(groupMembership);
        log.info("GroupMembership created [id={}, groupId={}, userId={}, role={}]",
                saved.getId(), group.getId(), userId, role);

        messageProducer.publish(
                new GroupMembershipCreatedEvent(saved.getId(), group.getId(), userId, role.name()),
                "",
                GroupMembershipCreatedContract.GROUP_MEMBERSHIP_CREATED_QUEUE_NAME
        );
        log.info("Event published for group membership creation [groupMembershipId={}]", saved.getId());
        return groupMembershipMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public GroupMembership getEntityById(UUID id) {
        return groupMembershipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GroupMembership with id=%s not found".formatted(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public GroupMembershipResponse getById(UUID id) {
        return groupMembershipMapper.toResponse(getEntityById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Paginated<GroupMembershipResponse> getAll(PaginationFilter filter) {
        Page<GroupMembership> groupMembershipPage = groupMembershipRepository.findAll(filter.toPageable());
        return Paginated.of(groupMembershipPage, groupMembershipMapper::toResponseList);
    }

    /**
     * Get the memberships of a group that have not been deleted, with pagination.
     *
     * @param groupId the ID of the group
     * @param filter pagination parameters
     * @return a paginated response object containing the memberships of the group
     */
    @Transactional(readOnly = true)
    public Paginated<GroupMembershipResponse> getAllByGroup(UUID groupId, PaginationFilter filter) {
        Page<GroupMembership> groupMembershipPage = groupMembershipRepository
                .findByGroupIdAndDeletedAtIsNull(groupId, filter.toPageable());
        return Paginated.of(groupMembershipPage, groupMembershipMapper::toResponseList);
    }

    @Override
    public GroupMembershipResponse update(UpdateGroupMembershipRequest updateGroupMembershipRequest) {
        GroupMembership groupMembership = getEntityById(updateGroupMembershipRequest.getId());

        if (updateGroupMembershipRequest.getRole() != GroupRole.ADMIN) {
            requireAnotherAdmin(groupMembership);
        }

        groupMembershipMapper.updateEntityFromRequest(groupMembership, updateGroupMembershipRequest);

        GroupMembership saved = groupMembershipRepository.save(groupMembership);
        log.info("GroupMembership updated [id={}, role={}]", saved.getId(), saved.getRole());

        messageProducer.publish(
                new GroupMembershipUpdatedEvent(
                        saved.getId(), saved.getGroup().getId(), saved.getUserId(), saved.getRole().name()),
                "",
                GroupMembershipUpdatedContract.GROUP_MEMBERSHIP_UPDATED_QUEUE_NAME
        );
        log.info("Event published for group membership update [groupMembershipId={}]", saved.getId());
        return groupMembershipMapper.toResponse(saved);
    }

    @Override
    public GroupMembershipResponse disable(UUID id) {
        GroupMembership groupMembership = getEntityById(id);
        requireAnotherAdmin(groupMembership);
        groupMembership.setDisabledAt(Instant.now());

        GroupMembership saved = groupMembershipRepository.save(groupMembership);
        log.info("GroupMembership disabled [id={}]", saved.getId());

        messageProducer.publish(
                new GroupMembershipDisabledEvent(
                        saved.getId(), saved.getGroup().getId(), saved.getUserId(), saved.getRole().name()),
                "",
                GroupMembershipDisabledContract.GROUP_MEMBERSHIP_DISABLED_QUEUE_NAME
        );
        log.info("Event published for group membership disabling [groupMembershipId={}]", saved.getId());
        return groupMembershipMapper.toResponse(saved);
    }

    @Override
    public GroupMembershipResponse delete(UUID id) {
        GroupMembership groupMembership = getEntityById(id);

        if (groupMembership.getDeletedAt() != null) {
            throw new ResourceNotFoundException("GroupMembership with id=%s not found".formatted(id));
        }

        requireAnotherAdmin(groupMembership);
        groupMembership.setDeletedAt(Instant.now());

        GroupMembership saved = groupMembershipRepository.save(groupMembership);
        log.info("GroupMembership deleted [id={}]", saved.getId());

        messageProducer.publish(
                new GroupMembershipDeletedEvent(
                        saved.getId(), saved.getGroup().getId(), saved.getUserId(), saved.getRole().name()),
                "",
                GroupMembershipDeletedContract.GROUP_MEMBERSHIP_DELETED_QUEUE_NAME
        );
        log.info("Event published for group membership deletion [groupMembershipId={}]", saved.getId());
        return groupMembershipMapper.toResponse(saved);
    }

    /**
     * Refuse to take the Admin role away from the last active Admin of a group.
     *
     * @param groupMembership the membership about to be demoted, disabled or deleted
     * @throws IllegalStateException if the membership is the only active Admin of its group
     */
    private void requireAnotherAdmin(GroupMembership groupMembership) {
        boolean isActiveAdmin = groupMembership.getRole() == GroupRole.ADMIN
                && groupMembership.getDeletedAt() == null
                && groupMembership.getDisabledAt() == null;

        if (!isActiveAdmin) {
            return;
        }

        UUID groupId = groupMembership.getGroup().getId();
        long activeAdmins = groupMembershipRepository
                .countByGroupIdAndRoleAndDeletedAtIsNullAndDisabledAtIsNull(groupId, GroupRole.ADMIN);

        if (activeAdmins <= 1) {
            throw new IllegalStateException(
                    "Group with id=%s must keep at least one Admin; promote another member first".formatted(groupId));
        }
    }
}
