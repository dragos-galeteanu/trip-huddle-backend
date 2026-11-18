package com.triphuddle.testsupport.security;

/**
 * The operations every {@code BaseAuthorizationPolicy} answers for.
 */
public enum Operation {
    CREATE,
    GET_BY_ID,
    GET_ALL,
    UPDATE,
    DISABLE,
    DELETE
}
