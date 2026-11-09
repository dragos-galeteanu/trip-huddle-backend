package com.triphuddle.user.mapper;

import com.triphuddle.common.mapper.BaseMapper;
import com.triphuddle.user.dto.DetailedUserResponse;
import com.triphuddle.user.dto.RegisterRequest;
import com.triphuddle.user.dto.UpdateUserRequest;
import com.triphuddle.user.dto.UserResponse;
import com.triphuddle.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface UserMapper extends BaseMapper<User, RegisterRequest, UpdateUserRequest, UserResponse> {

    @Override
    UserResponse toResponse(User user);

    DetailedUserResponse toDetailedUserResponse(UserResponse userResponse);

    @Override
    void updateEntityFromRequest(@MappingTarget User user, UpdateUserRequest request);
}
