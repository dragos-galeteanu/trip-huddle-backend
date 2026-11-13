package com.triphuddle.api.group.service;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.group.GroupCreatedContract;
import com.triphuddle.amqp.group.GroupCreatedEvent;
import com.triphuddle.amqp.group.GroupDeletedContract;
import com.triphuddle.amqp.group.GroupDeletedEvent;
import com.triphuddle.amqp.group.GroupDisabledContract;
import com.triphuddle.amqp.group.GroupDisabledEvent;
import com.triphuddle.amqp.group.GroupUpdatedContract;
import com.triphuddle.amqp.group.GroupUpdatedEvent;
import com.triphuddle.api.group.dto.CreateGroupMembershipRequest;
import com.triphuddle.api.group.dto.CreateGroupRequest;
import com.triphuddle.api.group.dto.GroupResponse;
import com.triphuddle.api.group.dto.UpdateGroupRequest;
import com.triphuddle.api.group.entity.Group;
import com.triphuddle.api.group.entity.GroupMembership;
import com.triphuddle.api.group.entity.GroupRole;
import com.triphuddle.api.group.mapper.GroupMapper;
import com.triphuddle.api.group.repository.GroupMembershipRepository;
import com.triphuddle.api.group.repository.GroupRepository;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.security.SecurityUtils;
import com.triphuddle.common.service.BaseJpaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Manages groups; the creator of a group becomes its first Admin.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class GroupService implements BaseJpaService<Group, CreateGroupRequest, UpdateGroupRequest, GroupResponse> {

    private final GroupRepository groupRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final GroupMapper groupMapper;
    private final GroupMembershipService groupMembershipService;
    private final RabbitMQMessageProducer messageProducer;

    @Override
    public GroupResponse create(CreateGroupRequest createGroupRequest) {
        UUID userId = SecurityUtils.extractUserId(SecurityContextHolder.getContext().getAuthentication());

        Group group = new Group();
        groupMapper.createEntityFromRequest(group, createGroupRequest);
        group.setCreatedByUserId(userId);

        Group saved = groupRepository.save(group);
        log.info("Group created [id={}, createdByUserId={}]", saved.getId(), userId);

        messageProducer.publish(
                new GroupCreatedEvent(saved.getId(), saved.getName()),
                "",
                GroupCreatedContract.GROUP_CREATED_QUEUE_NAME
        );
        log.info("Event published for group creation [groupId={}]", saved.getId());

        // The creator is the group's first Admin, so the group is never without one.
        groupMembershipService.create(new CreateGroupMembershipRequest(saved.getId(), userId, GroupRole.ADMIN));
        return groupMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Group getEntityById(UUID id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group with id=%s not found".formatted(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public GroupResponse getById(UUID id) {
        return groupMapper.toResponse(getEntityById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Paginated<GroupResponse> getAll(PaginationFilter filter) {
        Page<Group> groupPage = groupRepository.findAll(filter.toPageable());
        return Paginated.of(groupPage, groupMapper::toResponseList);
    }

    /**
     * Get the groups the current user is an active member of, with pagination.
     *
     * @param filter pagination parameters
     * @return a paginated response object containing the current user's groups
     */
    @Transactional(readOnly = true)
    public Paginated<GroupResponse> getMine(PaginationFilter filter) {
        UUID userId = SecurityUtils.extractUserId(SecurityContextHolder.getContext().getAuthentication());

        Page<GroupMembership> membershipPage = groupMembershipRepository
                .findByUserIdAndDeletedAtIsNullAndDisabledAtIsNull(userId, filter.toPageable());
        return Paginated.of(membershipPage, memberships -> memberships.stream()
                .map(GroupMembership::getGroup)
                .map(groupMapper::toResponse)
                .toList());
    }

    @Override
    public GroupResponse update(UpdateGroupRequest updateGroupRequest) {
        Group group = getEntityById(updateGroupRequest.getId());
        groupMapper.updateEntityFromRequest(group, updateGroupRequest);

        Group saved = groupRepository.save(group);
        log.info("Group updated [id={}]", saved.getId());

        messageProducer.publish(
                new GroupUpdatedEvent(saved.getId(), saved.getName()),
                "",
                GroupUpdatedContract.GROUP_UPDATED_QUEUE_NAME
        );
        log.info("Event published for group update [groupId={}]", saved.getId());
        return groupMapper.toResponse(saved);
    }

    @Override
    public GroupResponse disable(UUID id) {
        Group group = getEntityById(id);
        group.setDisabledAt(Instant.now());

        Group saved = groupRepository.save(group);
        log.info("Group disabled [id={}]", saved.getId());

        messageProducer.publish(
                new GroupDisabledEvent(saved.getId(), saved.getName()),
                "",
                GroupDisabledContract.GROUP_DISABLED_QUEUE_NAME
        );
        log.info("Event published for group disabling [groupId={}]", saved.getId());
        return groupMapper.toResponse(saved);
    }

    @Override
    public GroupResponse delete(UUID id) {
        Group group = getEntityById(id);
        group.setDeletedAt(Instant.now());

        Group saved = groupRepository.save(group);
        log.info("Group deleted [id={}]", saved.getId());

        messageProducer.publish(
                new GroupDeletedEvent(saved.getId(), saved.getName()),
                "",
                GroupDeletedContract.GROUP_DELETED_QUEUE_NAME
        );
        log.info("Event published for group deletion [groupId={}]", saved.getId());
        return groupMapper.toResponse(saved);
    }
}
