package com.triphuddle.user.mapper;

import com.triphuddle.common.mapper.BaseMapper;
import com.triphuddle.user.dto.CreateUserPreferenceRequest;
import com.triphuddle.user.dto.UpdateUserPreferenceRequest;
import com.triphuddle.user.dto.UserPreferenceResponse;
import com.triphuddle.user.entity.UserPreference;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

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
