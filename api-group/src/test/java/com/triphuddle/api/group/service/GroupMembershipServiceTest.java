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
import com.triphuddle.api.group.entity.Group;
import com.triphuddle.api.group.entity.GroupMembership;
import com.triphuddle.api.group.entity.GroupRole;
import com.triphuddle.api.group.fixtures.GroupFixtures;
import com.triphuddle.api.group.mapper.GroupMembershipMapper;
import com.triphuddle.api.group.repository.GroupMembershipRepository;
import com.triphuddle.api.group.repository.GroupRepository;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
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

import java.time.Instant;
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
class GroupMembershipServiceTest {

    @Mock
    GroupMembershipRepository groupMembershipRepository;

    @Mock
    GroupRepository groupRepository;

    @Mock
    RabbitMQMessageProducer messageProducer;

    @Spy
    GroupMembershipMapper groupMembershipMapper = Mappers.getMapper(GroupMembershipMapper.class);

    @InjectMocks
    GroupMembershipService groupMembershipService;

    private final Group group = GroupFixtures.group();

    @Nested
    class Create {

        @Test
        void addsTheUserToTheGroupAndPublishesToTheFanoutExchange() {
            CreateGroupMembershipRequest request = GroupFixtures.createMembership(group.getId()).build();
            given(groupRepository.findByIdAndDeletedAtIsNull(group.getId())).willReturn(Optional.of(group));
            given(groupMembershipRepository.saveAndFlush(any())).willAnswer(invocation -> {
                GroupMembership membership = invocation.getArgument(0);
                membership.setId(UUID.randomUUID());
                return membership;
            });

            GroupMembershipResponse response = groupMembershipService.create(request);

            assertThat(response.groupId()).isEqualTo(group.getId());
            assertThat(response.userId()).isEqualTo(request.getUserId());
            assertThat(response.role()).isEqualTo(GroupRole.MEMBER);
            then(messageProducer).should().publishAfterCommit(new GroupMembershipCreatedEvent(response.id(),
                            group.getId(), request.getUserId(), "MEMBER"),
                    GroupMembershipCreatedContract.GROUP_MEMBERSHIP_CREATED_EXCHANGE_NAME, "");
        }

        @Test
        void refusesASecondMembershipOfTheSameUser() {
            CreateGroupMembershipRequest request = GroupFixtures.createMembership(group.getId()).build();
            given(groupRepository.findByIdAndDeletedAtIsNull(group.getId())).willReturn(Optional.of(group));
            given(groupMembershipRepository.existsByGroupIdAndUserIdAndDeletedAtIsNull(group.getId(),
                    request.getUserId())).willReturn(true);

            assertThatThrownBy(() -> groupMembershipService.create(request))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("already exists");
            then(groupMembershipRepository).should(never()).saveAndFlush(any());
        }

        @Test
        void refusesAMissingOrDeletedGroup() {
            assertThatThrownBy(() -> groupMembershipService.create(GroupFixtures.createMembership(group.getId()).build()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class KeepingAnAdmin {

        @Test
        void refusesToDemoteTheLastAdmin() {
            GroupMembership admin = givenExisting(GroupRole.ADMIN);
            givenActiveAdmins(1);

            assertThatThrownBy(() -> groupMembershipService.update(
                    GroupFixtures.updateMembership(admin.getId(), GroupRole.MEMBER)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("at least one Admin");
        }

        @Test
        void refusesToDisableOrDeleteTheLastAdmin() {
            GroupMembership admin = givenExisting(GroupRole.ADMIN);
            givenActiveAdmins(1);

            assertThatThrownBy(() -> groupMembershipService.disable(admin.getId()))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> groupMembershipService.delete(admin.getId()))
                    .isInstanceOf(IllegalStateException.class);
            then(messageProducer).shouldHaveNoInteractions();
        }

        @Test
        void demotesAnAdminWhenAnotherRemainsAndPublishesUpdated() {
            GroupMembership admin = givenExisting(GroupRole.ADMIN);
            givenActiveAdmins(2);
            given(groupMembershipRepository.saveAndFlush(admin)).willReturn(admin);

            GroupMembershipResponse response = groupMembershipService.update(
                    GroupFixtures.updateMembership(admin.getId(), GroupRole.MEMBER));

            assertThat(response.role()).isEqualTo(GroupRole.MEMBER);
            then(messageProducer).should().publishAfterCommit(new GroupMembershipUpdatedEvent(admin.getId(),
                            group.getId(), admin.getUserId(), "MEMBER"),
                    GroupMembershipUpdatedContract.GROUP_MEMBERSHIP_UPDATED_EXCHANGE_NAME, "");
        }

        @Test
        void promotingNeedsNoCheck() {
            GroupMembership member = givenExisting(GroupRole.MEMBER);
            given(groupMembershipRepository.saveAndFlush(member)).willReturn(member);

            assertThat(groupMembershipService.update(GroupFixtures.updateMembership(member.getId(), GroupRole.ADMIN))
                    .role()).isEqualTo(GroupRole.ADMIN);
            then(groupMembershipRepository).should(never())
                    .countByGroupIdAndRoleAndDeletedAtIsNullAndDisabledAtIsNull(any(), any());
        }

        @Test
        void aDisabledAdminDoesNotCountAsTheAdminToKeep() {
            GroupMembership disabledAdmin = givenExisting(GroupRole.ADMIN);
            disabledAdmin.setDisabledAt(Instant.now());
            given(groupMembershipRepository.saveAndFlush(disabledAdmin)).willReturn(disabledAdmin);

            groupMembershipService.delete(disabledAdmin.getId());

            then(groupMembershipRepository).should(never())
                    .countByGroupIdAndRoleAndDeletedAtIsNullAndDisabledAtIsNull(any(), any());
        }
    }

    @Nested
    class DisableAndDelete {

        @Test
        void disablingAMemberPublishesDisabled() {
            GroupMembership member = givenExisting(GroupRole.MEMBER);
            given(groupMembershipRepository.saveAndFlush(member)).willReturn(member);

            assertThat(groupMembershipService.disable(member.getId()).disabledAt()).isNotNull();
            then(messageProducer).should().publishAfterCommit(new GroupMembershipDisabledEvent(member.getId(),
                            group.getId(), member.getUserId(), "MEMBER"),
                    GroupMembershipDisabledContract.GROUP_MEMBERSHIP_DISABLED_EXCHANGE_NAME, "");
        }

        @Test
        void deletingAMemberPublishesDeleted() {
            GroupMembership member = givenExisting(GroupRole.MEMBER);
            given(groupMembershipRepository.saveAndFlush(member)).willReturn(member);

            assertThat(groupMembershipService.delete(member.getId()).deletedAt()).isNotNull();
            then(messageProducer).should().publishAfterCommit(new GroupMembershipDeletedEvent(member.getId(),
                            group.getId(), member.getUserId(), "MEMBER"),
                    GroupMembershipDeletedContract.GROUP_MEMBERSHIP_DELETED_EXCHANGE_NAME, "");
        }

        @Test
        void aMissingOrDeletedMembershipIsNotFound() {
            UUID id = UUID.randomUUID();

            assertThatThrownBy(() -> groupMembershipService.getById(id)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> groupMembershipService.delete(id)).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    void listsMembershipsNotDeletedOverallAndByGroup() {
        GroupMembership member = GroupFixtures.membership(group, GroupRole.MEMBER);
        given(groupMembershipRepository.findAllByDeletedAtIsNull(PageRequest.of(0, 5)))
                .willReturn(new PageImpl<>(List.of(member)));
        given(groupMembershipRepository.findByGroupIdAndDeletedAtIsNull(group.getId(), PageRequest.of(0, 5)))
                .willReturn(new PageImpl<>(List.of(member)));

        Paginated<GroupMembershipResponse> all = groupMembershipService.getAll(new PaginationFilter(1, 5));
        Paginated<GroupMembershipResponse> ofGroup = groupMembershipService.getAllByGroup(group.getId(),
                new PaginationFilter(1, 5));

        assertThat(all.data()).extracting(GroupMembershipResponse::id).containsExactly(member.getId());
        assertThat(ofGroup.data()).extracting(GroupMembershipResponse::groupId).containsExactly(group.getId());
    }

    private GroupMembership givenExisting(GroupRole role) {
        GroupMembership membership = GroupFixtures.membership(group, role);
        given(groupMembershipRepository.findByIdAndDeletedAtIsNull(membership.getId()))
                .willReturn(Optional.of(membership));
        return membership;
    }

    private void givenActiveAdmins(long count) {
        given(groupMembershipRepository.countByGroupIdAndRoleAndDeletedAtIsNullAndDisabledAtIsNull(group.getId(),
                GroupRole.ADMIN)).willReturn(count);
    }
}
