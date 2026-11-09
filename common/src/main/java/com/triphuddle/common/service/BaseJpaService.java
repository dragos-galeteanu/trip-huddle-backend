package com.triphuddle.common.service;

import java.util.UUID;

public interface BaseJpaService<ENTITY, CREATE_REQUEST, UPDATE_REQUEST, RESPONSE>
        extends BaseService<CREATE_REQUEST, UPDATE_REQUEST, RESPONSE> {

    /**
     * Get entity by id.
     * @param id the id of the entity
     * @return the entity object
     */
    ENTITY getEntityById(UUID id);

    /**
     * Get a resource by its ID.
     * @param id the ID of the resource
     * @return the response object containing the resource
     */
    RESPONSE getById(UUID id);

    /**
     * Disable an existing resource.
     * @param id the ID of the resource
     * @return the response object containing the disabled resource
     */
    RESPONSE disable(UUID id);

    /**
     * Delete an existing resource.
     * @param id the ID of the resource
     * @return the response object containing the deleted resource
     */
    RESPONSE delete(UUID id);
}
