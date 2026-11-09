package com.triphuddle.common.controller;

import com.triphuddle.common.service.BaseMongoService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

public abstract class BaseMongoController<ENTITY, CREATE_REQUEST, UPDATE_REQUEST, RESPONSE>
        extends BaseController<CREATE_REQUEST, UPDATE_REQUEST, RESPONSE> {

    /**
     * Get the service associated with this controller.
     * @return the service
     */
    protected abstract BaseMongoService<ENTITY, CREATE_REQUEST, UPDATE_REQUEST, RESPONSE> getService();

    /**
     * Get resource by ID.
     * @param id the ID of the resource
     * @return the response object containing the resource
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get resource by ID")
    public ResponseEntity<RESPONSE> getById(@PathVariable String id) {
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
    public ResponseEntity<RESPONSE> disable(@PathVariable String id) {
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
    public ResponseEntity<RESPONSE> delete(@PathVariable String id) {
        RESPONSE response = getService().delete(id);
        return ResponseEntity.ok(response);
    }
}
