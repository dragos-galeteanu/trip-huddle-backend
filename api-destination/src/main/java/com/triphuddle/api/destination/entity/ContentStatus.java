package com.triphuddle.api.destination.entity;

/**
 * Where a piece of AI content is: being generated, ready, or failed on its last try.
 */
public enum ContentStatus {
    PENDING,
    READY,
    FAILED
}
