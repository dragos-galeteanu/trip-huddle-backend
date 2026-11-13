package com.triphuddle.api.group.service;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.group.GroupInvitationAcceptedContract;
import com.triphuddle.amqp.group.GroupInvitationAcceptedEvent;
import com.triphuddle.amqp.group.GroupInvitationCanceledContract;
import com.triphuddle.amqp.group.GroupInvitationCanceledEvent;
import com.triphuddle.amqp.group.GroupInvitationCreatedContract;
import com.triphuddle.amqp.group.GroupInvitationCreatedEvent;
import com.triphuddle.amqp.group.GroupInvitationDeletedContract;
import com.triphuddle.amqp.group.GroupInvitationDeletedEvent;
import com.triphuddle.amqp.group.GroupInvitationDisabledContract;
import com.triphuddle.amqp.group.GroupInvitationDisabledEvent;
import com.triphuddle.amqp.group.GroupInvitationRejectedContract;
import com.triphuddle.amqp.group.GroupInvitationRejectedEvent;
import com.triphuddle.amqp.group.GroupInvitationUpdatedContract;
import com.triphuddle.amqp.group.GroupInvitationUpdatedEvent;
import com.triphuddle.amqp.notification.NotificationEmailContract;
import com.triphuddle.amqp.notification.NotificationEmailType;
import com.triphuddle.amqp.notification.SendEmailRequest;
import com.triphuddle.api.group.dto.CreateGroupInvitationRequest;
import com.triphuddle.api.group.dto.CreateGroupMembershipRequest;
import com.triphuddle.api.group.dto.GroupInvitationResponse;
import com.triphuddle.api.group.dto.UpdateGroupInvitationRequest;
import com.triphuddle.api.group.entity.Group;
import com.triphuddle.api.group.entity.GroupInvitation;
import com.triphuddle.api.group.entity.GroupInvitationStatus;
import com.triphuddle.api.group.entity.GroupRole;
import com.triphuddle.api.group.mapper.GroupInvitationMapper;
import com.triphuddle.api.group.repository.GroupInvitationRepository;
import com.triphuddle.api.group.repository.GroupMembershipRepository;
import com.triphuddle.api.group.repository.GroupRepository;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.security.SecurityUtils;
import com.triphuddle.common.service.BaseJpaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class GroupInvitationService implements BaseJpaService<GroupInvitation, CreateGroupInvitationRequest,
        UpdateGroupInvitationRequest, GroupInvitationResponse> {

    private final GroupInvitationRepository groupInvitationRepository;
    private final GroupRepository groupRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final GroupInvitationMapper groupInvitationMapper;
    private final GroupMembershipService groupMembershipService;
    private final RabbitMQMessageProducer messageProducer;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.group-invitation.ttl-days}")
    private long ttlDays;

    @Override
    public GroupInvitationResponse create(CreateGroupInvitationRequest createGroupInvitationRequest) {
        UUID groupId = createGroupInvitationRequest.getGroupId();
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group with id=%s not found".formatted(groupId)));

        // Stored lower-cased so that the duplicate check and the later match on a user's email are exact.
        String email = createGroupInvitationRequest.getEmail().trim().toLowerCase(Locale.ROOT);

        if (groupInvitationRepository.existsByGroupIdAndEmailAndStatusAndDeletedAtIsNull(
                groupId, email, GroupInvitationStatus.PENDING)) {
            throw new IllegalStateException("A pending GroupInvitation with groupId=%s and email=%s already exists"
                    .formatted(groupId, email));
        }

        GroupInvitation groupInvitation = new GroupInvitation();
        groupInvitationMapper.createEntityFromRequest(groupInvitation, createGroupInvitationRequest);
        groupInvitation.setGroup(group);
        groupInvitation.setEmail(email);
        if (groupInvitation.getRole() == null) {
            groupInvitation.setRole(GroupRole.MEMBER);
        }
        groupInvitation.setToken(UUID.randomUUID());
        groupInvitation.setStatus(GroupInvitationStatus.PENDING);
        groupInvitation.setExpiresAt(Instant.now().plus(Duration.ofDays(ttlDays)));
        groupInvitation.setInvitedByUserId(
                SecurityUtils.extractUserId(SecurityContextHolder.getContext().getAuthentication()));

        GroupInvitation saved = groupInvitationRepository.save(groupInvitation);
        log.info("GroupInvitation created [id={}, groupId={}]", saved.getId(), groupId);

        messageProducer.publish(
                new GroupInvitationCreatedEvent(saved.getId(), groupId, saved.getEmail(), saved.getRole().name()),
                "",
                GroupInvitationCreatedContract.GROUP_INVITATION_CREATED_QUEUE_NAME
        );
        log.info("Event published for group invitation creation [groupInvitationId={}]", saved.getId());

        publishInvitationEmail(saved, group);
        return groupInvitationMapper.toResponse(saved);
    }

    private void publishInvitationEmail(GroupInvitation groupInvitation, Group group) {
        // The links open frontend pages that POST the token, so a mail scanner following them changes nothing.
        String acceptUrl = "%s/group-invitations/accept?token=%s".formatted(frontendUrl, groupInvitation.getToken());
        String rejectUrl = "%s/group-invitations/reject?token=%s".formatted(frontendUrl, groupInvitation.getToken());

        messageProducer.publish(
                new SendEmailRequest(
                        NotificationEmailType.GROUP_INVITATION,
                        groupInvitation.getEmail(),
                        Map.of(
                                "groupName", group.getName(),
                                "isAdminRole", groupInvitation.getRole() == GroupRole.ADMIN,
                                "acceptUrl", acceptUrl,
                                "rejectUrl", rejectUrl,
                                "expiresInDays", ttlDays
                        )
                ),
                "",
                NotificationEmailContract.EMAIL_QUEUE_NAME
        );
        log.info("Event published for group invitation email [groupInvitationId={}]", groupInvitation.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public GroupInvitation getEntityById(UUID id) {
        return groupInvitationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GroupInvitation with id=%s not found".formatted(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public GroupInvitationResponse getById(UUID id) {
        return groupInvitationMapper.toResponse(getEntityById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Paginated<GroupInvitationResponse> getAll(PaginationFilter filter) {
        Page<GroupInvitation> groupInvitationPage = groupInvitationRepository.findAll(filter.toPageable());
        return Paginated.of(groupInvitationPage, groupInvitationMapper::toResponseList);
    }

    @Transactional(readOnly = true)
    public Paginated<GroupInvitationResponse> getAllByGroup(UUID groupId, PaginationFilter filter) {
        Page<GroupInvitation> groupInvitationPage = groupInvitationRepository
                .findByGroupIdAndDeletedAtIsNull(groupId, filter.toPageable());
        return Paginated.of(groupInvitationPage, groupInvitationMapper::toResponseList);
    }

    @Override
    public GroupInvitationResponse update(UpdateGroupInvitationRequest updateGroupInvitationRequest) {
        GroupInvitation groupInvitation = getEntityById(updateGroupInvitationRequest.getId());
        requirePending(groupInvitation);

        groupInvitationMapper.updateEntityFromRequest(groupInvitation, updateGroupInvitationRequest);

        GroupInvitation saved = groupInvitationRepository.save(groupInvitation);
        log.info("GroupInvitation updated [id={}, role={}]", saved.getId(), saved.getRole());

        messageProducer.publish(
                new GroupInvitationUpdatedEvent(
                        saved.getId(), saved.getGroup().getId(), saved.getEmail(), saved.getRole().name()),
                "",
                GroupInvitationUpdatedContract.GROUP_INVITATION_UPDATED_QUEUE_NAME
        );
        log.info("Event published for group invitation update [groupInvitationId={}]", saved.getId());
        return groupInvitationMapper.toResponse(saved);
    }

    @Override
    public GroupInvitationResponse disable(UUID id) {
        GroupInvitation groupInvitation = getEntityById(id);
        groupInvitation.setDisabledAt(Instant.now());

        GroupInvitation saved = groupInvitationRepository.save(groupInvitation);
        log.info("GroupInvitation disabled [id={}]", saved.getId());

        messageProducer.publish(
                new GroupInvitationDisabledEvent(
                        saved.getId(), saved.getGroup().getId(), saved.getEmail(), saved.getRole().name()),
                "",
                GroupInvitationDisabledContract.GROUP_INVITATION_DISABLED_QUEUE_NAME
        );
        log.info("Event published for group invitation disabling [groupInvitationId={}]", saved.getId());
        return groupInvitationMapper.toResponse(saved);
    }

    @Override
    public GroupInvitationResponse delete(UUID id) {
        GroupInvitation groupInvitation = getEntityById(id);

        if (groupInvitation.getDeletedAt() != null) {
            throw new ResourceNotFoundException("GroupInvitation with id=%s not found".formatted(id));
        }

        groupInvitation.setDeletedAt(Instant.now());

        GroupInvitation saved = groupInvitationRepository.save(groupInvitation);
        log.info("GroupInvitation deleted [id={}]", saved.getId());

        messageProducer.publish(
                new GroupInvitationDeletedEvent(
                        saved.getId(), saved.getGroup().getId(), saved.getEmail(), saved.getRole().name()),
                "",
                GroupInvitationDeletedContract.GROUP_INVITATION_DELETED_QUEUE_NAME
        );
        log.info("Event published for group invitation deletion [groupInvitationId={}]", saved.getId());
        return groupInvitationMapper.toResponse(saved);
    }

    public GroupInvitationResponse accept(UUID token, Authentication authentication) {
        GroupInvitation groupInvitation = getEntityByToken(token);
        requirePending(groupInvitation);

        if (Instant.now().isAfter(groupInvitation.getExpiresAt())) {
            throw new IllegalStateException("GroupInvitation with id=%s has expired".formatted(groupInvitation.getId()));
        }

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt
                && !groupInvitation.getEmail().equalsIgnoreCase(jwt.getClaimAsString("email"))) {
            log.warn("GroupInvitation accept refused: signed-in email differs from the invited one [id={}]",
                    groupInvitation.getId());
            throw new AccessDeniedException("This invitation was sent to another email address");
        }

        groupInvitation.setStatus(GroupInvitationStatus.ACCEPTED);

        GroupInvitation saved = groupInvitationRepository.save(groupInvitation);
        log.info("GroupInvitation accepted [id={}]", saved.getId());

        // After the commit: api-user may answer at once, and its answer must find the invitation ACCEPTED.
        publishAfterCommit(
                new GroupInvitationAcceptedEvent(
                        saved.getId(), saved.getGroup().getId(), saved.getEmail(), saved.getRole().name()),
                GroupInvitationAcceptedContract.GROUP_INVITATION_ACCEPTED_QUEUE_NAME
        );
        return groupInvitationMapper.toResponse(saved);
    }

    public GroupInvitationResponse reject(UUID token) {
        GroupInvitation groupInvitation = getEntityByToken(token);
        requirePending(groupInvitation);

        groupInvitation.setStatus(GroupInvitationStatus.REJECTED);

        GroupInvitation saved = groupInvitationRepository.save(groupInvitation);
        log.info("GroupInvitation rejected [id={}]", saved.getId());

        messageProducer.publish(
                new GroupInvitationRejectedEvent(
                        saved.getId(), saved.getGroup().getId(), saved.getEmail(), saved.getRole().name()),
                "",
                GroupInvitationRejectedContract.GROUP_INVITATION_REJECTED_QUEUE_NAME
        );
        log.info("Event published for group invitation rejection [groupInvitationId={}]", saved.getId());
        return groupInvitationMapper.toResponse(saved);
    }

    public GroupInvitationResponse cancel(UUID id) {
        GroupInvitation groupInvitation = getEntityById(id);
        requirePending(groupInvitation);

        groupInvitation.setStatus(GroupInvitationStatus.CANCELED);

        GroupInvitation saved = groupInvitationRepository.save(groupInvitation);
        log.info("GroupInvitation canceled [id={}]", saved.getId());

        messageProducer.publish(
                new GroupInvitationCanceledEvent(
                        saved.getId(), saved.getGroup().getId(), saved.getEmail(), saved.getRole().name()),
                "",
                GroupInvitationCanceledContract.GROUP_INVITATION_CANCELED_QUEUE_NAME
        );
        log.info("Event published for group invitation cancellation [groupInvitationId={}]", saved.getId());
        return groupInvitationMapper.toResponse(saved);
    }

    public void linkUser(UUID groupInvitationId, UUID userId) {
        GroupInvitation groupInvitation = groupInvitationRepository.findById(groupInvitationId).orElse(null);

        if (groupInvitation == null) {
            log.warn("Cannot link user to group invitation: invitation not found [groupInvitationId={}]", groupInvitationId);
            return;
        }

        if (groupInvitation.getStatus() == GroupInvitationStatus.PENDING) {
            throw new IllegalStateException(
                    "GroupInvitation with id=%s is not accepted yet".formatted(groupInvitationId));
        }

        link(groupInvitation, userId);
    }

    public void linkVerifiedEmail(UUID userId, String email) {
        if (email == null) {
            return;
        }

        List<GroupInvitation> groupInvitations = groupInvitationRepository
                .findByEmailAndStatusAndAcceptedByUserIdIsNullAndDeletedAtIsNullAndDisabledAtIsNull(
                        email.trim().toLowerCase(Locale.ROOT), GroupInvitationStatus.ACCEPTED);

        groupInvitations.forEach(groupInvitation -> link(groupInvitation, userId));
    }

    private void link(GroupInvitation groupInvitation, UUID userId) {
        boolean isLinkable = groupInvitation.getStatus() == GroupInvitationStatus.ACCEPTED
                && groupInvitation.getAcceptedByUserId() == null
                && groupInvitation.getDeletedAt() == null
                && groupInvitation.getDisabledAt() == null;

        if (!isLinkable) {
            // Also the path a redelivered message takes, which makes linking safe to repeat.
            log.info("GroupInvitation not linked: already linked or no longer usable [id={}]", groupInvitation.getId());
            return;
        }

        Group group = groupInvitation.getGroup();

        if (group.getDeletedAt() != null) {
            log.info("GroupInvitation not linked: its group was deleted [id={}, groupId={}]",
                    groupInvitation.getId(), group.getId());
            return;
        }

        if (groupMembershipRepository.existsByGroupIdAndUserIdAndDeletedAtIsNull(group.getId(), userId)) {
            log.info("User is already a member of the group [groupId={}, userId={}]", group.getId(), userId);
        } else {
            groupMembershipService.create(
                    new CreateGroupMembershipRequest(group.getId(), userId, groupInvitation.getRole()));
        }

        groupInvitation.setAcceptedByUserId(userId);
        groupInvitationRepository.save(groupInvitation);
        log.info("GroupInvitation linked to user [id={}, userId={}]", groupInvitation.getId(), userId);
    }

    private GroupInvitation getEntityByToken(UUID token) {
        return groupInvitationRepository.findByTokenAndDeletedAtIsNullAndDisabledAtIsNull(token)
                .orElseThrow(() -> new ResourceNotFoundException("GroupInvitation with the given token not found"));
    }

    private void requirePending(GroupInvitation groupInvitation) {
        if (groupInvitation.getStatus() == GroupInvitationStatus.CANCELED) {
            throw new IllegalStateException(
                    "GroupInvitation with id=%s is no longer valid".formatted(groupInvitation.getId()));
        }

        if (groupInvitation.getStatus() != GroupInvitationStatus.PENDING) {
            throw new IllegalStateException("GroupInvitation with id=%s is already %s"
                    .formatted(groupInvitation.getId(), groupInvitation.getStatus().name().toLowerCase(Locale.ROOT)));
        }
    }

    private void publishAfterCommit(Object event, String queueName) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            messageProducer.publish(event, "", queueName);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                messageProducer.publish(event, "", queueName);
                log.info("Event published after commit [queue={}]", queueName);
            }
        });
    }
}
