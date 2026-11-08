package com.triphuddle.user.mapper;

import com.triphuddle.common.mapper.BaseMapper;
import com.triphuddle.user.dto.UpdateUserRequest;
import com.triphuddle.user.dto.UserResponse;
import com.triphuddle.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface UserMapper extends BaseMapper<User, UpdateUserRequest, UserResponse> {

    @Override
    UserResponse toResponse(User user);

    @Override
    void updateEntityFromRequest(@MappingTarget User user, UpdateUserRequest request);
}
