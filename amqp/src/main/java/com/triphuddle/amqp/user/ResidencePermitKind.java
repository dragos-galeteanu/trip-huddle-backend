package com.triphuddle.amqp.user;

/**
 * How lasting a residence permit is; shared by api-user, which records it, and api-destination, which reads it.
 */
public enum ResidencePermitKind {
    PERMANENT,
    TEMPORARY
}
