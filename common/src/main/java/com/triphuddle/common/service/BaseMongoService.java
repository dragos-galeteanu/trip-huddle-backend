package com.triphuddle.common.service;

public interface BaseMongoService<ENTITY, CREATE_REQUEST, UPDATE_REQUEST, RESPONSE>
        extends BaseService<CREATE_REQUEST, UPDATE_REQUEST, RESPONSE> {

    /**
     * Get entity by id.
     * @param id the id of the entity
     * @return the entity object
     */
    ENTITY getEntityById(String id);

    /**
     * Get a resource by its ID.
     * @param id the ID of the resource
     * @return the response object containing the resource
     */
    RESPONSE getById(String id);

    /**
     * Disable an existing resource.
     * @param id the ID of the resource
     * @return the response object containing the disabled resource
     */
    RESPONSE disable(String id);

    /**
     * Delete an existing resource.
     * @param id the ID of the resource
     * @return the response object containing the deleted resource
     */
    RESPONSE delete(String id);
}
