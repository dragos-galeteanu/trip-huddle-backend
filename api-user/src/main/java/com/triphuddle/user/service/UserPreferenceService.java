package com.triphuddle.user.service;

import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.dto.PaginationMetaData;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.service.BaseMongoService;
import com.triphuddle.user.dto.CreateUserPreferenceRequest;
import com.triphuddle.user.dto.UpdateUserPreferenceRequest;
import com.triphuddle.user.dto.UserPreferenceResponse;
import com.triphuddle.user.entity.UserPreference;
import com.triphuddle.user.mapper.UserPreferenceMapper;
import com.triphuddle.user.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
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
public class UserPreferenceService implements BaseMongoService<UserPreference, CreateUserPreferenceRequest, UpdateUserPreferenceRequest, UserPreferenceResponse> {

    private final UserPreferenceRepository userPreferenceRepository;
    private final UserPreferenceMapper userPreferenceMapper;
    private final UserService userService;

    @Override
    public UserPreferenceResponse create(CreateUserPreferenceRequest createUserPreferenceRequest) {
        UUID userId = createUserPreferenceRequest.getUserId();

        if (userPreferenceRepository.existsByUserId(userId)) {
            throw new IllegalStateException("UserPreference for userId=%s already exists".formatted(userId));
        }

        UserPreference userPreference = new UserPreference();
        userPreferenceMapper.createEntityFromRequest(userPreference, createUserPreferenceRequest);
        userPreference.setUserId(userService.getEntityById(userId).getId());

        UserPreference saved = userPreferenceRepository.save(userPreference);
        log.info("UserPreference created [id={}]", saved.getId());
        return userPreferenceMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UserPreference getEntityById(String id) {
        return userPreferenceRepository.findById(new ObjectId(id))
                .orElseThrow(() -> new ResourceNotFoundException("UserPreference with id=%s not found".formatted(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public UserPreferenceResponse getById(String id) {
        return userPreferenceMapper.toResponse(getEntityById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Paginated<UserPreferenceResponse> getAll(PaginationFilter filter) {
        Pageable pageable = PageRequest.of(filter.getPage() - 1, filter.getPageSize());
        Page<UserPreference> userPreferencePage = userPreferenceRepository.findAll(pageable);

        PaginationMetaData paginationMetaData = new PaginationMetaData(
                userPreferencePage.getTotalElements(),
                filter.getPageSize(),
                filter.getPage(),
                userPreferencePage.getTotalPages()
        );

        return new Paginated<>(
                userPreferenceMapper.toResponseList(userPreferencePage.getContent()),
                paginationMetaData
        );
    }

    @Override
    public UserPreferenceResponse update(UpdateUserPreferenceRequest updateUserPreferenceRequest) {
        UserPreference userPreference = getEntityById(updateUserPreferenceRequest.getId());

        if (!userPreference.getUserId().equals(updateUserPreferenceRequest.getUserId())) {
            throw new ResourceNotFoundException("UserPreference with id=%s and userId=%s not found"
                    .formatted(updateUserPreferenceRequest.getUserId(), updateUserPreferenceRequest.getUserId()));
        }

        userPreferenceMapper.updateEntityFromRequest(userPreference, updateUserPreferenceRequest);

        UserPreference saved = userPreferenceRepository.save(userPreference);
        log.info("UserPreference updated [id={}]", saved.getId());
        return userPreferenceMapper.toResponse(saved);
    }

    @Override
    public UserPreferenceResponse disable(String id) {
        UserPreference userPreference = getEntityById(id);
        userPreference.setDisabledAt(Instant.now());

        UserPreference saved = userPreferenceRepository.save(userPreference);
        log.info("UserPreference disabled [id={}]", saved.getId());
        return userPreferenceMapper.toResponse(saved);
    }

    @Override
    public UserPreferenceResponse delete(String id) {
        UserPreference userPreference = getEntityById(id);
        userPreference.setDeletedAt(Instant.now());

        UserPreference saved = userPreferenceRepository.save(userPreference);
        log.info("UserPreference deleted [id={}]", saved.getId());
        return userPreferenceMapper.toResponse(saved);
    }
}
