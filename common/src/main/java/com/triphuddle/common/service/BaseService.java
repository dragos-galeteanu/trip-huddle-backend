package com.triphuddle.common.service;

import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.filter.PaginationFilter;

import java.util.UUID;

public interface BaseService<ENTITY> {

    /**
     * Creates a new resource.
     * @param request the request object containing the data for creating the resource
     * @return the response object containing the created resource
     */
    <RESPONSE, CREATE_REQUEST> RESPONSE create(CREATE_REQUEST request);

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
    <RESPONSE> RESPONSE getById(UUID id);

    /**
     * Get all resources matching the filter, with pagination and sorting applied.
     * @param filter pagination parameters
     * @return a paginated response object containing the list of resources
     */
    <RESPONSE> Paginated<RESPONSE> getAll(PaginationFilter filter);

    /**
     * Update an existing resource.
     * @param request the request object containing the data for updating the resource
     * @return the response object containing the updated resource
     */
    <RESPONSE, UPDATE_REQUEST> RESPONSE update(UPDATE_REQUEST request);

    /**
     * Disable an existing resource.
     * @param id the ID of the resource
     * @return the response object containing the disabled resource
     */
    <RESPONSE> RESPONSE disable(UUID id);

    /**
     * Delete an existing resource.
     * @param id the ID of the resource
     * @return the response object containing the deleted resource
     */
    <RESPONSE> RESPONSE delete(UUID id);
}
