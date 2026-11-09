package com.triphuddle.user.service;

import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.dto.PaginationMetaData;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.service.BaseService;
import com.triphuddle.user.dto.CreateUserProfileRequest;
import com.triphuddle.user.dto.UpdateUserProfileRequest;
import com.triphuddle.user.dto.UserProfileResponse;
import com.triphuddle.user.entity.UserProfile;
import com.triphuddle.user.mapper.UserProfileMapper;
import com.triphuddle.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserProfileService implements BaseService<UserProfile, CreateUserProfileRequest, UpdateUserProfileRequest, UserProfileResponse> {

    private final UserProfileRepository userProfileRepository;
    private final UserProfileMapper userProfileMapper;
    private final UserService userService;

    @Override
    public UserProfileResponse create(CreateUserProfileRequest createUserProfileRequest) {
        UserProfile userProfile = new UserProfile();
        userProfileMapper.createEntityFromRequest(userProfile, createUserProfileRequest);
        userProfile.setUser(userService.getEntityById(createUserProfileRequest.getUserId()));

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
        Pageable pageable = PageRequest.of(filter.getPage() - 1, filter.getPageSize());
        Page<UserProfile> userProfilePage = userProfileRepository.findAll(pageable);

        PaginationMetaData paginationMetaData = new PaginationMetaData(
                userProfilePage.getTotalElements(),
                filter.getPageSize(),
                filter.getPage(),
                userProfilePage.getTotalPages()
        );

        return new Paginated<>(
                userProfileMapper.toResponseList(userProfilePage.getContent()),
                paginationMetaData
        );
    }

    @Override
    public UserProfileResponse update(UpdateUserProfileRequest updateUserProfileRequest) {
        UserProfile userProfile = this.getEntityById(updateUserProfileRequest.getId());

        if (!userProfile.getUser().getId().equals(updateUserProfileRequest.getUserId())) {
            throw new ResourceNotFoundException("UserProfile with id=%s and userId=%s not found"
                    .formatted(updateUserProfileRequest.getId(), updateUserProfileRequest.getUserId()));
        }

        userProfileMapper.updateEntityFromRequest(userProfile, updateUserProfileRequest);

        UserProfile saved = userProfileRepository.save(userProfile);
        log.info("UserProfile updated [id={}]", saved.getId());
        return userProfileMapper.toResponse(saved);
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
