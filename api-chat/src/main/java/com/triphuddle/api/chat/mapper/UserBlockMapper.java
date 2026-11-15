package com.triphuddle.api.chat.mapper;

import com.triphuddle.api.chat.dto.UserBlockResponse;
import com.triphuddle.api.chat.entity.UserBlock;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Maps {@link UserBlock} to its response DTO.
 */
@Mapper(componentModel = "spring")
public interface UserBlockMapper {

    /**
     * Converts a block to its response DTO.
     *
     * @param userBlock the block
     * @return the response DTO
     */
    @Mapping(target = "blockedUserId", source = "blockedId")
    UserBlockResponse toResponse(UserBlock userBlock);

    /**
     * Converts blocks to their response DTOs.
     *
     * @param userBlocks the blocks
     * @return the response DTOs
     */
    List<UserBlockResponse> toResponseList(List<UserBlock> userBlocks);
}
