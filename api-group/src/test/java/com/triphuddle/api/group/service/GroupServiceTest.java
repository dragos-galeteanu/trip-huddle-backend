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
import com.triphuddle.api.group.entity.GroupRole;
import com.triphuddle.api.group.fixtures.GroupFixtures;
import com.triphuddle.api.group.mapper.GroupMapper;
import com.triphuddle.api.group.repository.GroupMembershipRepository;
import com.triphuddle.api.group.repository.GroupRepository;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.testsupport.security.Caller;
import com.triphuddle.testsupport.security.SignedIn;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    GroupRepository groupRepository;

    @Mock
    GroupMembershipRepository groupMembershipRepository;

    @Mock
    GroupMembershipService groupMembershipService;

    @Mock
    RabbitMQMessageProducer messageProducer;

    @Spy
    GroupMapper groupMapper = Mappers.getMapper(GroupMapper.class);

    @InjectMocks
    GroupService groupService;

    private final UUID userId = UUID.randomUUID();

    @Nested
    class Create {

        @Test
        void savesTheGroupWithItsCreatorWhoBecomesItsFirstAdmin() {
            CreateGroupRequest request = GroupFixtures.createGroup().build();
            given(groupRepository.saveAndFlush(any())).willAnswer(invocation -> {
                Group group = invocation.getArgument(0);
                group.setId(UUID.randomUUID());
                return group;
            });

            GroupResponse response;
            try (SignedIn ignored = SignedIn.as(Caller.CUSTOMER, userId)) {
                response = groupService.create(request);
            }

            assertThat(response.name()).isEqualTo(request.getName());
            assertThat(response.createdByUserId()).isEqualTo(userId);
            then(groupMembershipService).should()
                    .create(new CreateGroupMembershipRequest(response.id(), userId, GroupRole.ADMIN));
            then(messageProducer).should().publishAfterCommit(new GroupCreatedEvent(response.id(), request.getName()),
                    "", GroupCreatedContract.GROUP_CREATED_QUEUE_NAME);
        }

        @Test
        void refusesACallerWithoutAPlatformUserId() {
            assertThatThrownBy(() -> groupService.create(GroupFixtures.createGroup().build()))
                    .isInstanceOf(AccessDeniedException.class);
            then(groupRepository).should(never()).saveAndFlush(any());
        }
    }

    @Nested
    class Read {

        @Test
        void findsAGroupThatIsNotDeleted() {
            Group group = givenExisting();

            assertThat(groupService.getById(group.getId()).name()).isEqualTo(group.getName());
        }

        @Test
        void treatsAMissingOrDeletedGroupAsNotFound() {
            UUID id = UUID.randomUUID();

            assertThatThrownBy(() -> groupService.getById(id)).isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void listsGroupsNotDeleted() {
            Group group = GroupFixtures.group();
            given(groupRepository.findAllByDeletedAtIsNull(PageRequest.of(0, 10)))
                    .willReturn(new PageImpl<>(List.of(group)));

            Paginated<GroupResponse> page = groupService.getAll(new PaginationFilter(1, 10));

            assertThat(page.data()).extracting(GroupResponse::id).containsExactly(group.getId());
        }

        @Test
        void listsTheCallersGroupsThroughTheirActiveMemberships() {
            Group group = GroupFixtures.group();
            given(groupMembershipRepository.findByUserIdAndDeletedAtIsNullAndDisabledAtIsNullAndGroupDeletedAtIsNull(
                    userId, PageRequest.of(0, 10)))
                    .willReturn(new PageImpl<>(List.of(GroupFixtures.membership(group, GroupRole.MEMBER))));

            Paginated<GroupResponse> page;
            try (SignedIn ignored = SignedIn.as(Caller.CUSTOMER, userId)) {
                page = groupService.getMine(new PaginationFilter(1, 10));
            }

            assertThat(page.data()).extracting(GroupResponse::id).containsExactly(group.getId());
        }
    }

    @Nested
    class Changes {

        @Test
        void updatingKeepsTheCreatorAndPublishesUpdated() {
            Group group = givenExisting();
            UUID creator = group.getCreatedByUserId();
            UpdateGroupRequest request = GroupFixtures.updateGroup(group.getId()).build();
            given(groupRepository.saveAndFlush(group)).willReturn(group);

            GroupResponse response = groupService.update(request);

            assertThat(response.name()).isEqualTo(request.getName());
            assertThat(response.createdByUserId()).isEqualTo(creator);
            then(messageProducer).should().publishAfterCommit(new GroupUpdatedEvent(group.getId(), request.getName()),
                    "", GroupUpdatedContract.GROUP_UPDATED_QUEUE_NAME);
        }

        @Test
        void disablingPublishesOnceToTheFanoutExchange() {
            Group group = givenExisting();
            given(groupRepository.saveAndFlush(group)).willReturn(group);

            assertThat(groupService.disable(group.getId()).disabledAt()).isNotNull();
            then(messageProducer).should().publishAfterCommit(new GroupDisabledEvent(group.getId(), group.getName()),
                    GroupDisabledContract.GROUP_DISABLED_EXCHANGE_NAME, "");
        }

        @Test
        void deletingPublishesOnceToTheFanoutExchange() {
            Group group = givenExisting();
            given(groupRepository.saveAndFlush(group)).willReturn(group);

            assertThat(groupService.delete(group.getId()).deletedAt()).isNotNull();
            then(messageProducer).should().publishAfterCommit(new GroupDeletedEvent(group.getId(), group.getName()),
                    GroupDeletedContract.GROUP_DELETED_EXCHANGE_NAME, "");
        }

        @Test
        void nothingChangesForAMissingOrDeletedGroup() {
            UUID id = UUID.randomUUID();

            assertThatThrownBy(() -> groupService.update(GroupFixtures.updateGroup(id).build()))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> groupService.disable(id)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> groupService.delete(id)).isInstanceOf(ResourceNotFoundException.class);
            then(messageProducer).shouldHaveNoInteractions();
        }
    }

    private Group givenExisting() {
        Group group = GroupFixtures.group();
        given(groupRepository.findByIdAndDeletedAtIsNull(group.getId())).willReturn(Optional.of(group));
        return group;
    }
}
