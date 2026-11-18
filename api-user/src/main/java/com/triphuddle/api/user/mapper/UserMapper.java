package com.triphuddle.api.user.mapper;

import com.triphuddle.common.mapper.BaseMapper;
import com.triphuddle.api.user.dto.DetailedUserResponse;
import com.triphuddle.api.user.dto.RegisterRequest;
import com.triphuddle.api.user.dto.UpdateUserRequest;
import com.triphuddle.api.user.dto.UserResponse;
import com.triphuddle.api.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Maps {@link User} to and from its request and response DTOs.
 */
@Mapper(componentModel = "spring")
public interface UserMapper extends BaseMapper<User, RegisterRequest, UpdateUserRequest, UserResponse> {

    @Override
    UserResponse toResponse(User user);

    /**
     * Converts a user to the detailed form staff see, with contact details.
     *
     * @param user the user
     * @return the detailed response
     */
    DetailedUserResponse toDetailedUserResponse(User user);

    // Users are created by registering, which sets each field itself; a register request maps only the names.
    @Override
    @Mapping(target = "id", ignore = true)
    void createEntityFromRequest(@MappingTarget User user, RegisterRequest request);

    // The entity was loaded by this ID; copying it again could only ever re-key the row.
    @Override
    @Mapping(target = "id", ignore = true)
    void updateEntityFromRequest(@MappingTarget User user, UpdateUserRequest request);
}
