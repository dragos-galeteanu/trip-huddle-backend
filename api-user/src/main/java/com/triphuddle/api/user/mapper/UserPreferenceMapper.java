package com.triphuddle.api.user.mapper;

import com.triphuddle.common.mapper.BaseMapper;
import com.triphuddle.api.user.dto.CreateUserPreferenceRequest;
import com.triphuddle.api.user.dto.UpdateUserPreferenceRequest;
import com.triphuddle.api.user.dto.UserPreferenceResponse;
import com.triphuddle.api.user.entity.UserPreference;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Maps {@link UserPreference} to and from its request and response DTOs.
 */
@Mapper(componentModel = "spring")
public interface UserPreferenceMapper extends BaseMapper<UserPreference, CreateUserPreferenceRequest,
        UpdateUserPreferenceRequest, UserPreferenceResponse> {

    @Override
    @Mapping(target = "isMarketingOptIn", source = "marketingOptIn")
    UserPreferenceResponse toResponse(UserPreference userPreference);

    @Override
    @Mapping(target = "marketingOptIn", source = "isMarketingOptIn")
    void createEntityFromRequest(@MappingTarget UserPreference userPreference, CreateUserPreferenceRequest request);

    @Override
    @Mapping(target = "marketingOptIn", source = "isMarketingOptIn")
    void updateEntityFromRequest(@MappingTarget UserPreference userPreference, UpdateUserPreferenceRequest request);
}
