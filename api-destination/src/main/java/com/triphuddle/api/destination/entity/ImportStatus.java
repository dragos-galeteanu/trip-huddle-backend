package com.triphuddle.api.destination.entity;

/**
 * Where an import is: waiting, running, done, or stopped by an error.
 */
public enum ImportStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED
}
