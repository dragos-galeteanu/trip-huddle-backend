package com.triphuddle.api.group.mapper;

import com.triphuddle.api.group.dto.CreateGroupInvitationRequest;
import com.triphuddle.api.group.dto.GroupInvitationResponse;
import com.triphuddle.api.group.dto.UpdateGroupInvitationRequest;
import com.triphuddle.api.group.entity.GroupInvitation;
import com.triphuddle.common.mapper.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface GroupInvitationMapper extends BaseMapper<GroupInvitation, CreateGroupInvitationRequest,
        UpdateGroupInvitationRequest, GroupInvitationResponse> {

    @Override
    @Mapping(target = "groupId", source = "group.id")
    GroupInvitationResponse toResponse(GroupInvitation groupInvitation);

    @Override
    @Mapping(target = "group", ignore = true)
    @Mapping(target = "token", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "expiresAt", ignore = true)
    @Mapping(target = "invitedByUserId", ignore = true)
    @Mapping(target = "acceptedByUserId", ignore = true)
    void createEntityFromRequest(@MappingTarget GroupInvitation groupInvitation, CreateGroupInvitationRequest request);

    @Override
    @Mapping(target = "group", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "token", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "expiresAt", ignore = true)
    @Mapping(target = "invitedByUserId", ignore = true)
    @Mapping(target = "acceptedByUserId", ignore = true)
    void updateEntityFromRequest(@MappingTarget GroupInvitation groupInvitation, UpdateGroupInvitationRequest request);
}
