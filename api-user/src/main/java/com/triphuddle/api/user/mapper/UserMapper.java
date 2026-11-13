package com.triphuddle.api.user.mapper;

import com.triphuddle.common.mapper.BaseMapper;
import com.triphuddle.api.user.dto.DetailedUserResponse;
import com.triphuddle.api.user.dto.RegisterRequest;
import com.triphuddle.api.user.dto.UpdateUserRequest;
import com.triphuddle.api.user.dto.UserResponse;
import com.triphuddle.api.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

/**
 * Maps {@link User} to and from its request and response DTOs.
 */
@Mapper(componentModel = "spring")
public interface UserMapper extends BaseMapper<User, RegisterRequest, UpdateUserRequest, UserResponse> {

    @Override
    UserResponse toResponse(User user);

    /**
     * Converts a user response to its detailed form.
     *
     * @param userResponse the response to convert
     * @return the detailed response
     */
    DetailedUserResponse toDetailedUserResponse(UserResponse userResponse);

    @Override
    void updateEntityFromRequest(@MappingTarget User user, UpdateUserRequest request);
}
