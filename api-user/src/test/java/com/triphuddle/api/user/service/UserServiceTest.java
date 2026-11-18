package com.triphuddle.api.user.service;

import com.google.firebase.auth.FirebaseAuthException;
import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.notification.NotificationEmailContract;
import com.triphuddle.amqp.notification.NotificationEmailType;
import com.triphuddle.amqp.notification.SendEmailRequest;
import com.triphuddle.amqp.user.UserContactUpdatedEvent;
import com.triphuddle.amqp.user.UserDeletedContract;
import com.triphuddle.amqp.user.UserDeletedEvent;
import com.triphuddle.amqp.user.UserDisabledContract;
import com.triphuddle.amqp.user.UserDisabledEvent;
import com.triphuddle.amqp.user.UserEmailVerifiedContract;
import com.triphuddle.amqp.user.UserEmailVerifiedEvent;
import com.triphuddle.amqp.user.UserRegisteredContract;
import com.triphuddle.amqp.user.UserRegisteredEvent;
import com.triphuddle.api.user.dto.UserResponse;
import com.triphuddle.api.user.entity.User;
import com.triphuddle.api.user.fixtures.UserFixtures;
import com.triphuddle.api.user.mapper.UserMapper;
import com.triphuddle.api.user.repository.UserRepository;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.security.UserRole;
import com.triphuddle.common.service.FirebaseUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    FirebaseUserService firebaseUserService;

    @Mock
    RabbitMQMessageProducer messageProducer;

    @Mock
    UserTravelDocumentService travelDocumentService;

    @Spy
    UserMapper userMapper = Mappers.getMapper(UserMapper.class);

    @InjectMocks
    UserService userService;

    @BeforeEach
    void configure() {
        ReflectionTestUtils.setField(userService, "baseUrl", "https://api.example");
    }

    @Nested
    class Register {

        private final Jwt token = Jwt.withTokenValue("t").header("alg", "none").subject("firebase-uid")
                .claim("email", "ana@example.com").build();

        @Test
        void createsACustomerSetsTheirClaimsAndSendsTheVerificationLink() throws Exception {
            givenSavedWithAnId();

            UserResponse response = userService.register(token, UserFixtures.register().firstName("Ana").build());

            assertThat(response.role()).isEqualTo(UserRole.Customer);
            then(firebaseUserService).should().setUserClaims("firebase-uid", response.id(), UserRole.Customer);
            SendEmailRequest email = emailOfType(NotificationEmailType.EMAIL_VERIFICATION);
            assertThat(email.recipientEmail()).isEqualTo("ana@example.com");
            assertThat((String) email.templateData().get("verificationLink"))
                    .startsWith("https://api.example/api/v1/users/verify-email?token=");
            then(messageProducer).should().publishAfterCommit(new UserRegisteredEvent(response.id()), "",
                    UserRegisteredContract.USER_REGISTERED_QUEUE_NAME);
            then(messageProducer).should().publishAfterCommit(any(UserContactUpdatedEvent.class), eq(""), anyString());
        }

        @Test
        void registeringAgainReturnsTheSameUser() {
            User existing = UserFixtures.user();
            given(userRepository.existsByFirebaseUid("firebase-uid")).willReturn(true);
            given(userRepository.findByFirebaseUid("firebase-uid")).willReturn(Optional.of(existing));

            assertThat(userService.register(token, UserFixtures.register().build()).id()).isEqualTo(existing.getId());
            then(userRepository).should(never()).saveAndFlush(any());
            then(messageProducer).shouldHaveNoInteractions();
        }

        @Test
        void leavesTheClaimsToTheSyncJobWhenFirebaseCannotBeReached() throws Exception {
            givenSavedWithAnId();
            willThrow(mock(FirebaseAuthException.class)).given(firebaseUserService)
                    .setUserClaims(anyString(), any(), any());

            UserResponse response = userService.register(token, UserFixtures.register().build());

            assertThat(response.id()).isNotNull();
            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            then(userRepository).should().saveAndFlush(saved.capture());
            assertThat(saved.getValue().getFirebaseSyncedRole()).as("left for FirebaseRoleSyncJob").isNull();
        }
    }

    @Nested
    class VerifyingTheEmail {

        @Test
        void verifiesOnceThenWelcomesAndLetsInvitationsComplete() {
            User user = UserFixtures.user();
            UUID token = UUID.randomUUID();
            user.setEmailVerificationToken(token);
            given(userRepository.findByEmailVerificationToken(token)).willReturn(Optional.of(user));

            userService.verifyEmail(token);

            assertThat(user.isEmailVerified()).isTrue();
            assertThat(user.getEmailVerificationToken()).as("single-use").isNull();
            then(messageProducer).should().publishAfterCommit(new UserEmailVerifiedEvent(user.getId(), user.getEmail()),
                    "", UserEmailVerifiedContract.USER_EMAIL_VERIFIED_QUEUE_NAME);
            assertThat(emailOfType(NotificationEmailType.WELCOME).templateData()).containsEntry("appUrl",
                    "https://api.example");
        }

        @Test
        void anAlreadyVerifiedAddressChangesNothing() {
            User user = UserFixtures.verifiedUser();
            UUID token = UUID.randomUUID();
            given(userRepository.findByEmailVerificationToken(token)).willReturn(Optional.of(user));

            userService.verifyEmail(token);

            then(messageProducer).shouldHaveNoInteractions();
        }

        @Test
        void anUnknownTokenIsNotFound() {
            assertThatThrownBy(() -> userService.verifyEmail(UUID.randomUUID()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Updating {

        @Test
        void aNewEmailMustBeVerifiedAgainAndGetsItsLink() {
            User user = givenExisting(UserFixtures.verifiedUser());

            userService.update(UserFixtures.update(user).email("new@example.com").build());

            assertThat(user.isEmailVerified()).isFalse();
            assertThat(user.getEmailVerificationToken()).isNotNull();
            SendEmailRequest link = emailOfType(NotificationEmailType.EMAIL_VERIFICATION);
            assertThat(link.recipientEmail()).isEqualTo("new@example.com");
            assertThat((String) link.templateData().get("verificationLink"))
                    .endsWith("token=" + user.getEmailVerificationToken());
            then(messageProducer).should().publishAfterCommit(any(UserContactUpdatedEvent.class), eq(""), anyString());
        }

        @Test
        void aNewNameUpdatesTheBillingContactButNeedsNoVerification() {
            User user = givenExisting(UserFixtures.verifiedUser());

            userService.update(UserFixtures.update(user).lastName("Ionescu").build());

            assertThat(user.isEmailVerified()).isTrue();
            then(messageProducer).should().publishAfterCommit(any(UserContactUpdatedEvent.class), eq(""), anyString());
            then(messageProducer).should(never()).publishAfterCommit(any(SendEmailRequest.class), anyString(),
                    anyString());
        }

        @Test
        void aNewPhoneNumberAloneTellsNobody() {
            User user = givenExisting(UserFixtures.verifiedUser());

            userService.update(UserFixtures.update(user).phoneNumber("+40799999999").build());

            assertThat(user.getPhoneNumber()).isEqualTo("+40799999999");
            then(messageProducer).shouldHaveNoInteractions();
        }
    }

    @Nested
    class DisablingAndDeleting {

        @Test
        void disablingTellsTheOtherServices() {
            User user = givenExisting(UserFixtures.user());

            assertThat(userService.disable(user.getId()).disabledAt()).isNotNull();
            then(messageProducer).should().publishAfterCommit(new UserDisabledEvent(user.getId()),
                    UserDisabledContract.USER_DISABLED_EXCHANGE_NAME, "");
        }

        @Test
        void deletingRemovesTheTravelDocumentsAndTellsTheOtherServices() {
            User user = givenExisting(UserFixtures.user());

            assertThat(userService.delete(user.getId()).deletedAt()).isNotNull();
            then(travelDocumentService).should().deleteForUser(user.getId());
            then(messageProducer).should().publishAfterCommit(new UserDeletedEvent(user.getId()),
                    UserDeletedContract.USER_DELETED_EXCHANGE_NAME, "");
        }

        @Test
        void aMissingOrDeletedUserIsNotFound() {
            assertThatThrownBy(() -> userService.delete(UUID.randomUUID()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class PasswordReset {

        @Test
        void emailsAResetLinkInTheBackground() throws Exception {
            User user = UserFixtures.user();
            given(userRepository.findByEmail(user.getEmail())).willReturn(Optional.of(user));
            given(firebaseUserService.generatePasswordResetLink(user.getEmail())).willReturn("https://reset.example");

            userService.resetPassword(user.getEmail());

            ArgumentCaptor<SendEmailRequest> email = ArgumentCaptor.forClass(SendEmailRequest.class);
            then(messageProducer).should(timeout(5000)).publishAfterCommit(email.capture(), eq(""),
                    eq(NotificationEmailContract.EMAIL_QUEUE_NAME));
            assertThat(email.getValue().templateData()).containsEntry("resetUrl", "https://reset.example");
        }

        @Test
        void sendsNothingForAnUnknownAddressOrWhenFirebaseFails() throws Exception {
            User user = UserFixtures.user();
            given(userRepository.findByEmail(user.getEmail())).willReturn(Optional.of(user));
            willThrow(mock(FirebaseAuthException.class)).given(firebaseUserService)
                    .generatePasswordResetLink(user.getEmail());

            userService.resetPassword("nobody@example.com");
            userService.resetPassword(user.getEmail());

            await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> then(messageProducer).shouldHaveNoInteractions());
            then(firebaseUserService).should(timeout(2000)).generatePasswordResetLink(user.getEmail());
        }
    }

    @Test
    void usersAreCreatedOnlyByRegistering() {
        assertThatThrownBy(() -> userService.create(UserFixtures.register().build()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void listsAndReadsUsersNotDeleted() {
        User user = UserFixtures.user();
        given(userRepository.findAllByDeletedAtIsNull(PageRequest.of(0, 5))).willReturn(new PageImpl<>(List.of(user)));
        given(userRepository.findByIdAndDeletedAtIsNull(user.getId())).willReturn(Optional.of(user));

        assertThat(userService.getAll(new PaginationFilter(1, 5)).data()).extracting(UserResponse::id)
                .containsExactly(user.getId());
        assertThat(userService.getById(user.getId()).firstName()).isEqualTo(user.getFirstName());
    }

    private void givenSavedWithAnId() {
        given(userRepository.saveAndFlush(any())).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(UUID.randomUUID());
            }
            return user;
        });
    }

    private User givenExisting(User user) {
        given(userRepository.findByIdAndDeletedAtIsNull(user.getId())).willReturn(Optional.of(user));
        given(userRepository.saveAndFlush(user)).willReturn(user);
        return user;
    }

    private SendEmailRequest emailOfType(NotificationEmailType type) {
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        then(messageProducer).should(org.mockito.Mockito.atLeastOnce()).publishAfterCommit(sent.capture(),
                anyString(), anyString());
        return sent.getAllValues().stream().filter(SendEmailRequest.class::isInstance).map(SendEmailRequest.class::cast)
                .filter(email -> email.type() == type).findFirst().orElseThrow();
    }
}
