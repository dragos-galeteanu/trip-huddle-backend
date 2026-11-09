package com.triphuddle.common.service;

import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.filter.PaginationFilter;

public interface BaseService<CREATE_REQUEST, UPDATE_REQUEST, RESPONSE> {

    /**
     * Creates a new resource.
     * @param request the request object containing the data for creating the resource
     * @return the response object containing the created resource
     */
    RESPONSE create(CREATE_REQUEST request);

    /**
     * Get all resources matching the filter, with pagination and sorting applied.
     * @param filter pagination parameters
     * @return a paginated response object containing the list of resources
     */
    Paginated<RESPONSE> getAll(PaginationFilter filter);

    /**
     * Update an existing resource.
     * @param request the request object containing the data for updating the resource
     * @return the response object containing the updated resource
     */
    RESPONSE update(UPDATE_REQUEST request);
}
