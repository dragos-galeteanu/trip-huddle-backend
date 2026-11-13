package com.triphuddle.api.user.service;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.notification.NotificationEmailContract;
import com.triphuddle.amqp.notification.NotificationEmailType;
import com.triphuddle.amqp.notification.SendEmailRequest;
import com.triphuddle.amqp.user.UserProfilePictureUpdatedContract;
import com.triphuddle.amqp.user.UserProfilePictureUpdatedEvent;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.service.BaseJpaService;
import com.triphuddle.api.user.dto.CreateUserProfileRequest;
import com.triphuddle.api.user.dto.UpdateUserProfileRequest;
import com.triphuddle.api.user.dto.UserProfileResponse;
import com.triphuddle.api.user.entity.User;
import com.triphuddle.api.user.entity.UserProfile;
import com.triphuddle.api.user.mapper.UserProfileMapper;
import com.triphuddle.api.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserProfileService implements BaseJpaService<UserProfile, CreateUserProfileRequest, UpdateUserProfileRequest, UserProfileResponse> {

    private final UserProfileRepository userProfileRepository;
    private final UserProfileMapper userProfileMapper;
    private final UserService userService;
    private final RabbitMQMessageProducer messageProducer;

    @Override
    public UserProfileResponse create(CreateUserProfileRequest createUserProfileRequest) {
        UUID userId = createUserProfileRequest.getUserId();

        if (userProfileRepository.existsByUserId(userId)) {
            throw new IllegalStateException("UserProfile with userId=%s already exists".formatted(userId));
        }

        UserProfile userProfile = new UserProfile();
        userProfileMapper.createEntityFromRequest(userProfile, createUserProfileRequest);
        userProfile.setUser(userService.getEntityById(userId));

        UserProfile saved = userProfileRepository.save(userProfile);
        log.info("UserProfile created [id={}]", saved.getId());
        return userProfileMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfile getEntityById(UUID id) {
        return userProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UserProfile with id=%s not found".formatted(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getById(UUID id) {
        return userProfileMapper.toResponse(getEntityById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Paginated<UserProfileResponse> getAll(PaginationFilter filter) {
        Page<UserProfile> userProfilePage = userProfileRepository.findAll(filter.toPageable());
        return Paginated.of(userProfilePage, userProfileMapper::toResponseList);
    }

    @Override
    public UserProfileResponse update(UpdateUserProfileRequest updateUserProfileRequest) {
        UserProfile userProfile = this.getEntityById(updateUserProfileRequest.getId());

        if (!userProfile.getUser().getId().equals(updateUserProfileRequest.getUserId())) {
            throw new ResourceNotFoundException("UserProfile with id=%s and userId=%s not found"
                    .formatted(updateUserProfileRequest.getId(), updateUserProfileRequest.getUserId()));
        }

        String previousProfilePictureUrl = userProfile.getProfilePictureUrl();
        String previousCoverPictureUrl = userProfile.getCoverPictureUrl();

        userProfileMapper.updateEntityFromRequest(userProfile, updateUserProfileRequest);

        UserProfile saved = userProfileRepository.save(userProfile);
        log.info("UserProfile updated [id={}]", saved.getId());

        publishPictureUpdatedIfChanged(saved, previousProfilePictureUrl, previousCoverPictureUrl);
        return userProfileMapper.toResponse(saved);
    }

    /**
     * Publish a picture-updated event and its notification email if either picture URL changed, and nothing otherwise.
     *
     * @param userProfile the saved profile
     * @param previousProfilePictureUrl the profile picture URL as it was before the update
     * @param previousCoverPictureUrl the cover picture URL as it was before the update
     */
    private void publishPictureUpdatedIfChanged(UserProfile userProfile,
                                                String previousProfilePictureUrl,
                                                String previousCoverPictureUrl) {

        boolean profilePictureChanged = !Objects.equals(previousProfilePictureUrl, userProfile.getProfilePictureUrl());
        boolean coverPictureChanged = !Objects.equals(previousCoverPictureUrl, userProfile.getCoverPictureUrl());

        if (!profilePictureChanged && !coverPictureChanged) {
            return;
        }

        messageProducer.publish(
                new UserProfilePictureUpdatedEvent(
                        userProfile.getUser().getId(),
                        userProfile.getId(),
                        userProfile.getProfilePictureUrl(),
                        userProfile.getCoverPictureUrl(),
                        profilePictureChanged,
                        coverPictureChanged
                ),
                "",
                UserProfilePictureUpdatedContract.USER_PROFILE_PICTURE_UPDATED_QUEUE_NAME
        );
        log.info("Event published for user profile picture update "
                        + "[userProfileId={}, profilePictureChanged={}, coverPictureChanged={}]",
                userProfile.getId(), profilePictureChanged, coverPictureChanged);

        User user = userProfile.getUser();
        messageProducer.publish(
                new SendEmailRequest(
                        NotificationEmailType.PROFILE_PICTURE_UPDATED,
                        user.getEmail(),
                        Map.of(
                                "firstName", user.getFirstName(),
                                "profilePictureChanged", profilePictureChanged,
                                "coverPictureChanged", coverPictureChanged
                        )
                ),
                "",
                NotificationEmailContract.EMAIL_QUEUE_NAME
        );
        log.info("Event published for user profile picture updated email [userProfileId={}]", userProfile.getId());
    }

    @Override
    public UserProfileResponse disable(UUID id) {
        UserProfile userProfile = getEntityById(id);
        userProfile.setDisabledAt(Instant.now());

        UserProfile saved = userProfileRepository.save(userProfile);
        log.info("UserProfile disabled [id={}]", saved.getId());
        return userProfileMapper.toResponse(saved);
    }

    @Override
    public UserProfileResponse delete(UUID id) {
        UserProfile userProfile = getEntityById(id);
        userProfile.setDeletedAt(Instant.now());

        UserProfile saved = userProfileRepository.save(userProfile);
        log.info("UserProfile deleted [id={}]", saved.getId());
        return userProfileMapper.toResponse(saved);
    }
}
