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
import com.triphuddle.api.group.entity.Group;
import com.triphuddle.api.group.entity.GroupInvitation;
import com.triphuddle.api.group.entity.GroupInvitationStatus;
import com.triphuddle.api.group.entity.GroupRole;
import com.triphuddle.api.group.fixtures.GroupFixtures;
import com.triphuddle.api.group.mapper.GroupInvitationMapper;
import com.triphuddle.api.group.repository.GroupInvitationRepository;
import com.triphuddle.api.group.repository.GroupMembershipRepository;
import com.triphuddle.api.group.repository.GroupRepository;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.testsupport.security.Caller;
import com.triphuddle.testsupport.security.SignedIn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class GroupInvitationServiceTest {

    @Mock
    GroupInvitationRepository groupInvitationRepository;

    @Mock
    GroupRepository groupRepository;

    @Mock
    GroupMembershipRepository groupMembershipRepository;

    @Mock
    GroupMembershipService groupMembershipService;

    @Mock
    RabbitMQMessageProducer messageProducer;

    @Spy
    GroupInvitationMapper groupInvitationMapper = Mappers.getMapper(GroupInvitationMapper.class);

    @InjectMocks
    GroupInvitationService service;

    private final Group group = GroupFixtures.group();
    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void configure() {
        ReflectionTestUtils.setField(service, "frontendUrl", "https://app.example");
        ReflectionTestUtils.setField(service, "ttlDays", 7L);
    }

    @Nested
    class Create {

        @Test
        void invitesALowerCasedEmailForSevenDaysWithAFreshToken() {
            given(groupRepository.findByIdAndDeletedAtIsNull(group.getId())).willReturn(Optional.of(group));
            givenSaveAssignsAnId();

            GroupInvitationResponse response = create(GroupFixtures.createInvitation(group.getId())
                    .email("  Friend@Example.COM ").role(null).build());

            assertThat(response.email()).isEqualTo("friend@example.com");
            assertThat(response.role()).as("the default role").isEqualTo(GroupRole.MEMBER);
            assertThat(response.status()).isEqualTo(GroupInvitationStatus.PENDING);
            assertThat(response.invitedByUserId()).isEqualTo(adminId);
            assertThat(response.expiresAt())
                    .isCloseTo(Instant.now().plus(Duration.ofDays(7)), within(1, ChronoUnit.MINUTES));
            then(messageProducer).should().publishAfterCommit(new GroupInvitationCreatedEvent(response.id(),
                    group.getId(), "friend@example.com", "MEMBER"), "",
                    GroupInvitationCreatedContract.GROUP_INVITATION_CREATED_QUEUE_NAME);
        }

        @Test
        void emailsTheInviteeLinksToTheFrontendPagesThatPostTheToken() {
            given(groupRepository.findByIdAndDeletedAtIsNull(group.getId())).willReturn(Optional.of(group));
            ArgumentCaptor<GroupInvitation> saved = givenSaveAssignsAnId();

            create(GroupFixtures.createInvitation(group.getId()).role(GroupRole.ADMIN).build());

            ArgumentCaptor<SendEmailRequest> email = ArgumentCaptor.forClass(SendEmailRequest.class);
            then(messageProducer).should().publishAfterCommit(email.capture(), eq(""),
                    eq(NotificationEmailContract.EMAIL_QUEUE_NAME));
            UUID token = saved.getValue().getToken();
            assertThat(email.getValue().type()).isEqualTo(NotificationEmailType.GROUP_INVITATION);
            assertThat(email.getValue().templateData())
                    .containsEntry("groupName", group.getName())
                    .containsEntry("isAdminRole", true)
                    .containsEntry("acceptUrl", "https://app.example/group-invitations/accept?token=" + token)
                    .containsEntry("rejectUrl", "https://app.example/group-invitations/reject?token=" + token)
                    .containsEntry("expiresInDays", 7L);
        }

        @Test
        void refusesASecondPendingInvitationForTheSameEmail() {
            given(groupRepository.findByIdAndDeletedAtIsNull(group.getId())).willReturn(Optional.of(group));
            given(groupInvitationRepository.existsByGroupIdAndEmailAndStatusAndDeletedAtIsNull(group.getId(),
                    "friend@example.com", GroupInvitationStatus.PENDING)).willReturn(true);

            assertThatThrownBy(() -> create(GroupFixtures.createInvitation(group.getId())
                    .email("FRIEND@example.com").build()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("pending");
            then(messageProducer).shouldHaveNoInteractions();
        }

        @Test
        void refusesAMissingOrDeletedGroup() {
            assertThatThrownBy(() -> create(GroupFixtures.createInvitation(group.getId()).build()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        private GroupInvitationResponse create(CreateGroupInvitationRequest request) {
            try (SignedIn ignored = SignedIn.as(Caller.CUSTOMER, adminId)) {
                return service.create(request);
            }
        }
    }

    @Nested
    class Answering {

        @Test
        void acceptingMarksItAcceptedAndPublishesOnlyAfterCommit() {
            GroupInvitation invitation = givenByToken(GroupInvitationStatus.PENDING);

            GroupInvitationResponse response = service.accept(invitation.getToken(), signedInAs(invitation.getEmail()));

            assertThat(response.status()).isEqualTo(GroupInvitationStatus.ACCEPTED);
            then(messageProducer).should().publishAfterCommit(new GroupInvitationAcceptedEvent(invitation.getId(),
                    group.getId(), invitation.getEmail(), "MEMBER"), "",
                    GroupInvitationAcceptedContract.GROUP_INVITATION_ACCEPTED_QUEUE_NAME);
        }

        @Test
        void anInviteeNotSignedInMayAcceptAndSignUpLater() {
            GroupInvitation invitation = givenByToken(GroupInvitationStatus.PENDING);

            assertThat(service.accept(invitation.getToken(), null).status()).isEqualTo(GroupInvitationStatus.ACCEPTED);
        }

        @Test
        void refusesSomeoneSignedInWithAnotherAddress() {
            GroupInvitation invitation = givenByToken(GroupInvitationStatus.PENDING);

            assertThatThrownBy(() -> service.accept(invitation.getToken(), signedInAs("someone-else@example.com")))
                    .isInstanceOf(AccessDeniedException.class);
            assertThat(invitation.getStatus()).isEqualTo(GroupInvitationStatus.PENDING);
        }

        @Test
        void matchesTheSignedInAddressWhateverItsCase() {
            GroupInvitation invitation = givenByToken(GroupInvitationStatus.PENDING);

            assertThat(service.accept(invitation.getToken(), signedInAs(invitation.getEmail().toUpperCase())).status())
                    .isEqualTo(GroupInvitationStatus.ACCEPTED);
        }

        @Test
        void refusesAnExpiredInvitation() {
            GroupInvitation invitation = givenByToken(GroupInvitationStatus.PENDING);
            invitation.setExpiresAt(Instant.now().minusSeconds(1));

            assertThatThrownBy(() -> service.accept(invitation.getToken(), null))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("expired");
        }

        @Test
        void rejectingAndCancelingPublishTheirEvents() {
            GroupInvitation toReject = givenByToken(GroupInvitationStatus.PENDING);
            GroupInvitation toCancel = givenById(GroupInvitationStatus.PENDING);

            assertThat(service.reject(toReject.getToken()).status()).isEqualTo(GroupInvitationStatus.REJECTED);
            assertThat(service.cancel(toCancel.getId()).status()).isEqualTo(GroupInvitationStatus.CANCELED);
            then(messageProducer).should().publishAfterCommit(new GroupInvitationRejectedEvent(toReject.getId(),
                    group.getId(), toReject.getEmail(), "MEMBER"), "",
                    GroupInvitationRejectedContract.GROUP_INVITATION_REJECTED_QUEUE_NAME);
            then(messageProducer).should().publishAfterCommit(new GroupInvitationCanceledEvent(toCancel.getId(),
                    group.getId(), toCancel.getEmail(), "MEMBER"), "",
                    GroupInvitationCanceledContract.GROUP_INVITATION_CANCELED_QUEUE_NAME);
        }

        @ParameterizedTest
        @EnumSource(value = GroupInvitationStatus.class, names = {"ACCEPTED", "REJECTED", "CANCELED"})
        void anInvitationIsAnsweredOnlyOnce(GroupInvitationStatus status) {
            GroupInvitation invitation = givenByToken(status);

            assertThatThrownBy(() -> service.accept(invitation.getToken(), null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(status == GroupInvitationStatus.CANCELED ? "no longer valid" : "already");
            assertThatThrownBy(() -> service.reject(invitation.getToken())).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void anUnknownTokenIsNotFound() {
            assertThatThrownBy(() -> service.accept(UUID.randomUUID(), null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Linking {

        @Test
        void makesAnAcceptedInvitationAMembershipWithItsRole() {
            GroupInvitation invitation = GroupFixtures.invitation(group, GroupInvitationStatus.ACCEPTED);
            invitation.setRole(GroupRole.ADMIN);
            UUID userId = UUID.randomUUID();
            given(groupInvitationRepository.findById(invitation.getId())).willReturn(Optional.of(invitation));

            service.linkUser(invitation.getId(), userId);

            then(groupMembershipService).should().create(new CreateGroupMembershipRequest(group.getId(), userId,
                    GroupRole.ADMIN));
            assertThat(invitation.getAcceptedByUserId()).isEqualTo(userId);
            then(groupInvitationRepository).should().saveAndFlush(invitation);
        }

        @Test
        void linksAUserWhoIsAlreadyAMemberWithoutASecondMembership() {
            GroupInvitation invitation = GroupFixtures.invitation(group, GroupInvitationStatus.ACCEPTED);
            UUID userId = UUID.randomUUID();
            given(groupInvitationRepository.findById(invitation.getId())).willReturn(Optional.of(invitation));
            given(groupMembershipRepository.existsByGroupIdAndUserIdAndDeletedAtIsNull(group.getId(), userId))
                    .willReturn(true);

            service.linkUser(invitation.getId(), userId);

            then(groupMembershipService).shouldHaveNoInteractions();
            assertThat(invitation.getAcceptedByUserId()).isEqualTo(userId);
        }

        @Test
        void doesNothingTheSecondTimeSoARedeliveredMessageIsHarmless() {
            GroupInvitation invitation = GroupFixtures.invitation(group, GroupInvitationStatus.ACCEPTED);
            invitation.setAcceptedByUserId(UUID.randomUUID());
            given(groupInvitationRepository.findById(invitation.getId())).willReturn(Optional.of(invitation));

            service.linkUser(invitation.getId(), UUID.randomUUID());

            then(groupMembershipService).shouldHaveNoInteractions();
            then(groupInvitationRepository).should(never()).saveAndFlush(any());
        }

        @Test
        void skipsAnInvitationOfADeletedGroupOrThatNoLongerExists() {
            GroupInvitation invitation = GroupFixtures.invitation(group, GroupInvitationStatus.ACCEPTED);
            group.setDeletedAt(Instant.now());
            given(groupInvitationRepository.findById(invitation.getId())).willReturn(Optional.of(invitation));

            service.linkUser(invitation.getId(), UUID.randomUUID());
            service.linkUser(UUID.randomUUID(), UUID.randomUUID());

            then(groupMembershipService).shouldHaveNoInteractions();
        }

        @Test
        void refusesToLinkAnInvitationNotAcceptedYet() {
            GroupInvitation invitation = GroupFixtures.invitation(group, GroupInvitationStatus.PENDING);
            given(groupInvitationRepository.findById(invitation.getId())).willReturn(Optional.of(invitation));

            assertThatThrownBy(() -> service.linkUser(invitation.getId(), UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("not accepted yet");
        }

        @Test
        void aVerifiedEmailLinksEveryAcceptedInvitationSentToIt() {
            GroupInvitation first = GroupFixtures.invitation(group, GroupInvitationStatus.ACCEPTED);
            GroupInvitation second = GroupFixtures.invitation(GroupFixtures.group(), GroupInvitationStatus.ACCEPTED);
            UUID userId = UUID.randomUUID();
            given(groupInvitationRepository
                    .findByEmailAndStatusAndAcceptedByUserIdIsNullAndDeletedAtIsNullAndDisabledAtIsNull(
                            "friend@example.com", GroupInvitationStatus.ACCEPTED)).willReturn(List.of(first, second));

            service.linkVerifiedEmail(userId, " Friend@Example.com");

            assertThat(first.getAcceptedByUserId()).isEqualTo(userId);
            assertThat(second.getAcceptedByUserId()).isEqualTo(userId);
        }

        @Test
        void aUserWithoutAnEmailLinksNothing() {
            service.linkVerifiedEmail(UUID.randomUUID(), null);

            then(groupInvitationRepository).shouldHaveNoInteractions();
        }
    }

    @Nested
    class Managing {

        @Test
        void changesTheRoleOfAPendingInvitationAndPublishesUpdated() {
            GroupInvitation invitation = givenById(GroupInvitationStatus.PENDING);
            given(groupInvitationRepository.saveAndFlush(invitation)).willReturn(invitation);

            GroupInvitationResponse response = service.update(GroupFixtures.updateInvitation(invitation.getId(),
                    GroupRole.ADMIN));

            assertThat(response.role()).isEqualTo(GroupRole.ADMIN);
            assertThat(response.email()).as("the address never changes").isEqualTo(invitation.getEmail());
            then(messageProducer).should().publishAfterCommit(new GroupInvitationUpdatedEvent(invitation.getId(),
                    group.getId(), invitation.getEmail(), "ADMIN"), "",
                    GroupInvitationUpdatedContract.GROUP_INVITATION_UPDATED_QUEUE_NAME);
        }

        @Test
        void refusesToChangeAnAnsweredInvitation() {
            GroupInvitation invitation = givenById(GroupInvitationStatus.ACCEPTED);

            assertThatThrownBy(() -> service.update(GroupFixtures.updateInvitation(invitation.getId(), GroupRole.ADMIN)))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> service.cancel(invitation.getId())).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void disablingAndDeletingPublishTheirEvents() {
            GroupInvitation invitation = givenById(GroupInvitationStatus.PENDING);
            given(groupInvitationRepository.saveAndFlush(invitation)).willReturn(invitation);

            service.disable(invitation.getId());
            service.delete(invitation.getId());

            then(messageProducer).should().publishAfterCommit(new GroupInvitationDisabledEvent(invitation.getId(),
                    group.getId(), invitation.getEmail(), "MEMBER"), "",
                    GroupInvitationDisabledContract.GROUP_INVITATION_DISABLED_QUEUE_NAME);
            then(messageProducer).should().publishAfterCommit(new GroupInvitationDeletedEvent(invitation.getId(),
                    group.getId(), invitation.getEmail(), "MEMBER"), "",
                    GroupInvitationDeletedContract.GROUP_INVITATION_DELETED_QUEUE_NAME);
        }

        @Test
        void aMissingOrDeletedInvitationIsNotFound() {
            assertThatThrownBy(() -> service.getById(UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void listsInvitationsNotDeletedOverallAndByGroup() {
            GroupInvitation invitation = GroupFixtures.invitation(group, GroupInvitationStatus.PENDING);
            given(groupInvitationRepository.findAllByDeletedAtIsNull(PageRequest.of(0, 5)))
                    .willReturn(new PageImpl<>(List.of(invitation)));
            given(groupInvitationRepository.findByGroupIdAndDeletedAtIsNull(group.getId(), PageRequest.of(0, 5)))
                    .willReturn(new PageImpl<>(List.of(invitation)));

            assertThat(service.getAll(new PaginationFilter(1, 5)).data()).hasSize(1);
            assertThat(service.getAllByGroup(group.getId(), new PaginationFilter(1, 5)).data()).hasSize(1);
        }
    }

    private ArgumentCaptor<GroupInvitation> givenSaveAssignsAnId() {
        ArgumentCaptor<GroupInvitation> saved = ArgumentCaptor.forClass(GroupInvitation.class);
        given(groupInvitationRepository.saveAndFlush(saved.capture())).willAnswer(invocation -> {
            GroupInvitation invitation = invocation.getArgument(0);
            invitation.setId(UUID.randomUUID());
            return invitation;
        });
        return saved;
    }

    private GroupInvitation givenByToken(GroupInvitationStatus status) {
        GroupInvitation invitation = GroupFixtures.invitation(group, status);
        given(groupInvitationRepository.findByTokenAndDeletedAtIsNullAndDisabledAtIsNull(invitation.getToken()))
                .willReturn(Optional.of(invitation));
        lenient().when(groupInvitationRepository.saveAndFlush(invitation)).thenReturn(invitation);
        return invitation;
    }

    private GroupInvitation givenById(GroupInvitationStatus status) {
        GroupInvitation invitation = GroupFixtures.invitation(group, status);
        given(groupInvitationRepository.findByIdAndDeletedAtIsNull(invitation.getId()))
                .willReturn(Optional.of(invitation));
        lenient().when(groupInvitationRepository.saveAndFlush(invitation)).thenReturn(invitation);
        return invitation;
    }

    private static Authentication signedInAs(String email) {
        return new JwtAuthenticationToken(Jwt.withTokenValue("t").header("alg", "none")
                .claim("userId", UUID.randomUUID().toString()).claim("email", email).build(), List.of());
    }
}
