package com.triphuddle.common.service;

import java.util.UUID;

/**
 * Adds the operations addressed by UUID: get, disable and delete.
 *
 * @param <ENTITY> the type of the entity
 * @param <CREATE_REQUEST> the type of the create request
 * @param <UPDATE_REQUEST> the type of the update request
 * @param <RESPONSE> the type of the response
 */
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
