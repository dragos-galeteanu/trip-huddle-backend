package com.triphuddle.common.mapper;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Base mapper interface for converting between entities and DTOs.
 * @param <ENTITY> The entity type.
 * @param <REQUEST> The request DTO type.
 * @param <RESPONSE> The response DTO type.
 */
public interface BaseMapper<ENTITY, REQUEST, RESPONSE> {

    /**
     * Converts an entity to a response DTO.
     * @param entity The entity to convert.
     * @return The response DTO.
     */
    RESPONSE toResponse(ENTITY entity);

    /**
     * Populates an entity's fields from a request DTO.
     * Used for both creating new entities and updating existing ones.
     *
     * @param entity The entity to populate (can be new or existing).
     * @param request The request DTO containing field values.
     */
    void updateEntityFromRequest(ENTITY entity, REQUEST request);

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
