package com.triphuddle.common.controller;

import com.triphuddle.common.service.BaseJpaService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Adds the endpoints addressed by UUID: get, disable and delete.
 *
 * @param <ENTITY> the type of the entity
 * @param <CREATE_REQUEST> the type of the create request
 * @param <UPDATE_REQUEST> the type of the update request
 * @param <RESPONSE> the type of the response
 */
public abstract class BaseJpaController<ENTITY, CREATE_REQUEST, UPDATE_REQUEST, RESPONSE>
        extends BaseController<CREATE_REQUEST, UPDATE_REQUEST, RESPONSE> {

    /**
     * Get the service associated with this controller.
     * @return the service
     */
    protected abstract BaseJpaService<ENTITY, CREATE_REQUEST, UPDATE_REQUEST, RESPONSE> getService();

    /**
     * Get resource by ID.
     * @param id the ID of the resource
     * @return the response object containing the resource
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get resource by ID")
    public ResponseEntity<RESPONSE> getById(@PathVariable UUID id) {
        RESPONSE response = getService().getById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Disable an existing resource by ID.
     * @param id the ID of the resource
     * @return the response object containing the disabled resource
     */
    @DeleteMapping("/{id}/disable")
    @Operation(summary = "Disable an existing resource by ID")
    public ResponseEntity<RESPONSE> disable(@PathVariable UUID id) {
        RESPONSE response = getService().disable(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete an existing resource by ID.
     * @param id the ID of the resource
     * @return the response object containing the deleted resource
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an existing resource by ID")
    public ResponseEntity<RESPONSE> delete(@PathVariable UUID id) {
        RESPONSE response = getService().delete(id);
        return ResponseEntity.ok(response);
    }
}
