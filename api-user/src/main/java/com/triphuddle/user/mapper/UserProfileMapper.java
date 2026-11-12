package com.triphuddle.user.mapper;

import com.triphuddle.common.mapper.BaseMapper;
import com.triphuddle.user.dto.CreateUserProfileRequest;
import com.triphuddle.user.dto.UpdateUserProfileRequest;
import com.triphuddle.user.dto.UserProfileResponse;
import com.triphuddle.user.entity.UserProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface UserProfileMapper extends BaseMapper<UserProfile, CreateUserProfileRequest, UpdateUserProfileRequest, UserProfileResponse> {

    @Override
    @Mapping(target = "xUrl", expression = "java(userProfile.getXUrl())")
    UserProfileResponse toResponse(UserProfile userProfile);

    @Override
    void createEntityFromRequest(@MappingTarget UserProfile userProfile, CreateUserProfileRequest request);

    @Override
    void updateEntityFromRequest(@MappingTarget UserProfile userProfile, UpdateUserProfileRequest request);
}
