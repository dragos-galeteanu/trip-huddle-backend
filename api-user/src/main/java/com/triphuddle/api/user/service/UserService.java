package com.triphuddle.api.user.service;

import com.google.firebase.auth.FirebaseAuthException;
import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.notification.NotificationEmailContract;
import com.triphuddle.amqp.notification.NotificationEmailType;
import com.triphuddle.amqp.notification.SendEmailRequest;
import com.triphuddle.amqp.user.UserRegisteredContract;
import com.triphuddle.amqp.user.UserRegisteredEvent;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.dto.PaginationMetaData;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.security.UserRole;
import com.triphuddle.common.service.BaseJpaService;
import com.triphuddle.common.service.FirebaseUserService;
import com.triphuddle.api.user.dto.RegisterRequest;
import com.triphuddle.api.user.dto.UpdateUserRequest;
import com.triphuddle.api.user.dto.UserResponse;
import com.triphuddle.api.user.entity.User;
import com.triphuddle.api.user.mapper.UserMapper;
import com.triphuddle.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService implements BaseJpaService<User, RegisterRequest, UpdateUserRequest, UserResponse> {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final FirebaseUserService firebaseUserService;
    private final RabbitMQMessageProducer messageProducer;

    @Value("${app.base-url}")
    private String baseUrl;

    @Transactional
    public UserResponse register(Jwt jwt, RegisterRequest registerRequest) {
        String firebaseUid = jwt.getSubject();

        if (userRepository.existsByFirebaseUid(firebaseUid)) {
            return userMapper.toResponse(
                    userRepository.findByFirebaseUid(firebaseUid)
                            .orElseThrow(() -> new ResourceNotFoundException("User with firebaseUid=%s not found".formatted(firebaseUid))));
        }

        User user = new User();
        user.setFirebaseUid(firebaseUid);
        user.setEmail(jwt.getClaimAsString("email"));
        user.setFirstName(registerRequest.getFirstName());
        user.setLastName(registerRequest.getLastName());
        user.setPhoneNumber(registerRequest.getPhoneNumber());
        user.setRole(UserRole.Customer);
        user.setEmailVerificationToken(UUID.randomUUID());
        User saved = userRepository.save(user);
        log.info("User registered [id={}]", saved.getId());

        try {
            firebaseUserService.setUserClaims(firebaseUid, saved.getId(), saved.getRole());
            saved.setFirebaseSyncedRole(saved.getRole());
            userRepository.save(saved);
            log.info("Firebase synced role for user [id={}] set to {}", saved.getId() ,saved.getFirebaseSyncedRole());
        } catch (FirebaseAuthException e) {
            log.warn("Firebase role sync deferred - FirebaseRoleSyncJob will retry "
                            + "[userId={}, firebaseUid={}, error={}]",
                    saved.getId(), firebaseUid, e.getMessage());
        }

        publishVerificationEmail(saved);
        publishUserRegistered(saved);
        return userMapper.toResponse(saved);
    }

    private void publishUserRegistered(User user) {
        messageProducer.publish(
                new UserRegisteredEvent(user.getId()),
                "",
                UserRegisteredContract.USER_REGISTERED_QUEUE_NAME
        );
        log.info("Event published for user registration [userId={}]", user.getId());
    }

    private void publishVerificationEmail(User user) {
        String verificationLink = baseUrl + "/api/v1/users/verify-email?token=" + user.getEmailVerificationToken();
        messageProducer.publish(
                new SendEmailRequest(
                        NotificationEmailType.EMAIL_VERIFICATION,
                        user.getEmail(),
                        Map.of(
                                "firstName", user.getFirstName(),
                                "verificationLink", verificationLink
                        )
                ),
                "",
                NotificationEmailContract.EMAIL_QUEUE_NAME
        );
        log.info("Event published for user verification email [userId={}]", user.getId());
    }

    @Transactional
    public void verifyEmail(UUID token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("User with emailVerificationToken=%s not found".formatted(token)));

        if (user.isEmailVerified()) {
            log.info("Email address of [userId={}] is already verified", user.getId());
            return;
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        userRepository.save(user);
        log.info("Email address of [userId={}] is verified", user.getId());

        messageProducer.publish(
                new SendEmailRequest(
                        NotificationEmailType.WELCOME,
                        user.getEmail(),
                        Map.of(
                                "firstName", user.getFirstName(),
                                "appUrl", baseUrl
                        )
                ),
                "",
                NotificationEmailContract.EMAIL_QUEUE_NAME
        );
        log.info("Event published for welcoming user email [userId={}]",  user.getId());
    }

    public void resetPassword(String email) {
        CompletableFuture.runAsync(() -> {
            var userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                log.warn("Password reset requested for unknown email: {}", email);
                return;
            }

            User user = userOpt.get();
            try {
                String resetLink = firebaseUserService.generatePasswordResetLink(email);
                messageProducer.publish(
                        new SendEmailRequest(
                                NotificationEmailType.PASSWORD_RESET,
                                email,
                                Map.of(
                                        "firstName", user.getFirstName(),
                                        "resetUrl", resetLink
                                )
                        ),
                        "",
                        NotificationEmailContract.EMAIL_QUEUE_NAME
                );
                log.info("Event publish for user's password reset email [email={}]", email);
            } catch (FirebaseAuthException e) {
                log.error("Failed to generate password reset link for {}: {}", email, e.getMessage());
            }
        });
    }

    @Override
    public UserResponse create(RegisterRequest registerRequest) {
        throw new UnsupportedOperationException("Use the /register endpoint to create a new user");
    }

    @Override
    @Transactional(readOnly = true)
    public User getEntityById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User with id=%s not found".formatted(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return userMapper.toResponse(getEntityById(id));
    }

    @Override
    public Paginated<UserResponse> getAll(PaginationFilter filter) {
        Pageable pageable = PageRequest.of(filter.getPage() - 1, filter.getPageSize());
        Page<User> userPage = userRepository.findAll(pageable);

        PaginationMetaData paginationMetaData = new PaginationMetaData(
                userPage.getTotalElements(),
                filter.getPageSize(),
                filter.getPage(),
                userPage.getTotalPages()
        );

        return new Paginated<>(
                userMapper.toResponseList(userPage.getContent()),
                paginationMetaData
        );
    }

    @Override
    @Transactional
    public UserResponse update(UpdateUserRequest updateUserRequest) {
        User user = this.getEntityById(updateUserRequest.getId());

        boolean isEmailChanged = !user.getEmail().equals(updateUserRequest.getEmail());

        userMapper.updateEntityFromRequest(user, updateUserRequest);

        if (isEmailChanged) {
            user.setEmailVerified(false);
            user.setEmailVerificationToken(UUID.randomUUID());
        }

        User saved = userRepository.save(user);
        log.info("User updated [id={}]", saved.getId());
        return userMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse disable(UUID id) {
        User user = getEntityById(id);
        user.setDisabledAt(Instant.now());

        User saved = userRepository.save(user);
        log.info("User disabled [id={}]", saved.getId());
        return userMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse delete(UUID id) {
        User user = getEntityById(id);
        user.setDeletedAt(Instant.now());

        User saved = userRepository.save(user);
        log.info("User deleted [id={}]", saved.getId());
        return userMapper.toResponse(saved);
    }
}
