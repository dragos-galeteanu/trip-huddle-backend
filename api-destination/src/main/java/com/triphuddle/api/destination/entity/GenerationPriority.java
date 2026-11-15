package com.triphuddle.api.destination.entity;

/**
 * Why content is being generated, which decides what the daily budget lets through.
 */
public enum GenerationPriority {
    /** Someone is looking at a place without it; goes until the hard limit. */
    VIEW,
    /** Content that aged out; stops at the soft budget. */
    REFRESH,
    /** Staff asked for it. */
    STAFF
}
