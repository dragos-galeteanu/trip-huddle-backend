package com.triphuddle.api.user.mapper;

import com.triphuddle.common.mapper.BaseMapper;
import com.triphuddle.api.user.dto.CreateUserPreferenceRequest;
import com.triphuddle.api.user.dto.UpdateUserPreferenceRequest;
import com.triphuddle.api.user.dto.UserPreferenceResponse;
import com.triphuddle.api.user.entity.UserPreference;
import org.bson.types.ObjectId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Maps {@link UserPreference} to and from its request and response DTOs.
 */
@Mapper(componentModel = "spring")
public interface UserPreferenceMapper extends BaseMapper<UserPreference, CreateUserPreferenceRequest,
        UpdateUserPreferenceRequest, UserPreferenceResponse> {

    @Override
    @Mapping(target = "isMarketingOptIn", source = "marketingOptIn")
    UserPreferenceResponse toResponse(UserPreference userPreference);

    @Override
    @Mapping(target = "marketingOptIn", source = "isMarketingOptIn")
    void createEntityFromRequest(@MappingTarget UserPreference userPreference, CreateUserPreferenceRequest request);

    // The entity was loaded by this ID; copying it again could only ever re-key the row.
    @Override
    @Mapping(target = "marketingOptIn", source = "isMarketingOptIn")
    @Mapping(target = "id", ignore = true)
    void updateEntityFromRequest(@MappingTarget UserPreference userPreference, UpdateUserPreferenceRequest request);

    /**
     * A document's ID as clients see it: its hex string.
     *
     * @param id the ID
     * @return the hex string, or null for none
     */
    default String map(ObjectId id) {
        return id == null ? null : id.toHexString();
    }
}
