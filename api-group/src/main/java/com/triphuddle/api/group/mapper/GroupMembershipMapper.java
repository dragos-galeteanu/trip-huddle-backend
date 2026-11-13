package com.triphuddle.api.group.mapper;

import com.triphuddle.api.group.dto.CreateGroupMembershipRequest;
import com.triphuddle.api.group.dto.GroupMembershipResponse;
import com.triphuddle.api.group.dto.UpdateGroupMembershipRequest;
import com.triphuddle.api.group.entity.GroupMembership;
import com.triphuddle.common.mapper.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Maps {@link GroupMembership} to and from its request and response DTOs.
 */
@Mapper(componentModel = "spring")
public interface GroupMembershipMapper extends BaseMapper<GroupMembership, CreateGroupMembershipRequest,
        UpdateGroupMembershipRequest, GroupMembershipResponse> {

    @Override
    @Mapping(target = "groupId", source = "group.id")
    GroupMembershipResponse toResponse(GroupMembership groupMembership);

    @Override
    @Mapping(target = "group", ignore = true)
    void createEntityFromRequest(@MappingTarget GroupMembership groupMembership, CreateGroupMembershipRequest request);

    @Override
    @Mapping(target = "group", ignore = true)
    @Mapping(target = "userId", ignore = true)
    void updateEntityFromRequest(@MappingTarget GroupMembership groupMembership, UpdateGroupMembershipRequest request);
}
