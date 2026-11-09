package com.triphuddle.common.controller;

import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.service.BaseService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

public abstract class BaseController<CREATE_REQUEST, UPDATE_REQUEST, RESPONSE> {

    /**
     * Get the service associated with this controller.
     * @return the service
     */
    protected abstract BaseService<CREATE_REQUEST, UPDATE_REQUEST, RESPONSE> getService();

    /**
     * Create a new resource.
     * @param request the request object containing the data for creating the resource
     * @return the response object containing the created resource
     */
    @PostMapping
    @Operation(summary = "Create a new resource")
    public ResponseEntity<RESPONSE> create(@Valid @RequestBody CREATE_REQUEST request) {
        RESPONSE response = getService().create(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Get all resources with pagination.
     * @param filter pagination filter
     * @return a paginated response object containing the list of resources
     */
    @GetMapping
    @Operation(summary = "Get all resources with pagination")
    public ResponseEntity<Paginated<RESPONSE>> getAll(@Valid @ModelAttribute PaginationFilter filter) {
        Paginated<RESPONSE> responses = getService().getAll(filter);
        return ResponseEntity.ok(responses);
    }

    /**
     * Update an existing resource.
     * @param request the request object containing the data for updating the resource
     * @return the response object containing the updated resource
     */
    @PutMapping
    @Operation(summary = "Update resource")
    public ResponseEntity<RESPONSE> update(@Valid @RequestBody UPDATE_REQUEST request) {
        RESPONSE response = getService().update(request);
        return ResponseEntity.ok(response);
    }
}
