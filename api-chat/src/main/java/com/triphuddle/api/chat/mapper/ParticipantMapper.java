package com.triphuddle.api.chat.mapper;

import com.triphuddle.api.chat.dto.ParticipantResponse;
import com.triphuddle.api.chat.entity.ConversationParticipant;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Maps {@link ConversationParticipant} to its response DTO.
 */
@Mapper(componentModel = "spring")
public interface ParticipantMapper {

    /**
     * Converts a participant to its response DTO.
     *
     * @param participant the participant
     * @return the response DTO
     */
    ParticipantResponse toResponse(ConversationParticipant participant);

    /**
     * Converts participants to their response DTOs.
     *
     * @param participants the participants
     * @return the response DTOs
     */
    List<ParticipantResponse> toResponseList(List<ConversationParticipant> participants);
}
