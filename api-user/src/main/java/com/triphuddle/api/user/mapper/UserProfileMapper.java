package com.triphuddle.api.user.mapper;

import com.triphuddle.common.mapper.BaseMapper;
import com.triphuddle.api.user.dto.CreateUserProfileRequest;
import com.triphuddle.api.user.dto.UpdateUserProfileRequest;
import com.triphuddle.api.user.dto.UserProfileResponse;
import com.triphuddle.api.user.entity.UserProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Maps {@link UserProfile} to and from its request and response DTOs.
 */
@Mapper(componentModel = "spring")
public interface UserProfileMapper extends BaseMapper<UserProfile, CreateUserProfileRequest, UpdateUserProfileRequest, UserProfileResponse> {

    @Override
    @Mapping(target = "xUrl", expression = "java(userProfile.getXUrl())")
    @Mapping(target = "userId", source = "user.id")
    UserProfileResponse toResponse(UserProfile userProfile);

    @Override
    void createEntityFromRequest(@MappingTarget UserProfile userProfile, CreateUserProfileRequest request);

    // The entity was loaded by this ID; copying it again could only ever re-key the row.
    @Override
    @Mapping(target = "id", ignore = true)
    void updateEntityFromRequest(@MappingTarget UserProfile userProfile, UpdateUserProfileRequest request);
}
