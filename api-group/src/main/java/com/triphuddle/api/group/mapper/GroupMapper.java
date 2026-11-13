package com.triphuddle.api.group.mapper;

import com.triphuddle.api.group.dto.CreateGroupRequest;
import com.triphuddle.api.group.dto.GroupResponse;
import com.triphuddle.api.group.dto.UpdateGroupRequest;
import com.triphuddle.api.group.entity.Group;
import com.triphuddle.common.mapper.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Maps {@link Group} to and from its request and response DTOs.
 */
@Mapper(componentModel = "spring")
public interface GroupMapper extends BaseMapper<Group, CreateGroupRequest, UpdateGroupRequest, GroupResponse> {

    @Override
    GroupResponse toResponse(Group group);

    @Override
    @Mapping(target = "createdByUserId", ignore = true)
    void createEntityFromRequest(@MappingTarget Group group, CreateGroupRequest request);

    @Override
    @Mapping(target = "createdByUserId", ignore = true)
    void updateEntityFromRequest(@MappingTarget Group group, UpdateGroupRequest request);
}
