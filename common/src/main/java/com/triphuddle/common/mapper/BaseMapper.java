package com.triphuddle.common.mapper;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Base mapper interface for converting between entities and DTOs.
 */
public interface BaseMapper<ENTITY, CREATE_REQUEST, UPDATE_REQUEST, RESPONSE> {

    /**
     * Converts an entity to a response DTO.
     * @param entity The entity to convert.
     * @return The response DTO.
     */
    RESPONSE toResponse(ENTITY entity);

    /**
     * Populates an entity's fields from a create request DTO.
     *
     * @param entity The entity to populate.
     * @param request The request DTO containing field values.
     */
    void createEntityFromRequest(ENTITY entity, CREATE_REQUEST request);

    /**
     * Populates an entity's fields from an update request DTO.
     *
     * @param entity The entity to populate.
     * @param request The request DTO containing field values.
     */
    void updateEntityFromRequest(ENTITY entity, UPDATE_REQUEST request);

    /**
     * Converts a list of entities to a list of response DTOs.
     * @param entities The list of entities to convert.
     * @return The list of response DTOs.
     */
    default List<RESPONSE> toResponseList(List<ENTITY> entities) {
        return entities.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}
