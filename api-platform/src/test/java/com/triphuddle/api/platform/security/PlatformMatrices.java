package com.triphuddle.api.platform.security;

import com.triphuddle.testsupport.security.AuthorizationMatrix;

import static com.triphuddle.testsupport.security.Caller.ADMIN;
import static com.triphuddle.testsupport.security.Caller.SUPPORT;
import static com.triphuddle.testsupport.security.Operation.CREATE;
import static com.triphuddle.testsupport.security.Operation.DELETE;
import static com.triphuddle.testsupport.security.Operation.DISABLE;
import static com.triphuddle.testsupport.security.Operation.GET_ALL;
import static com.triphuddle.testsupport.security.Operation.GET_BY_ID;
import static com.triphuddle.testsupport.security.Operation.UPDATE;

/**
 * Who may do what to the platform's reference data (PLT-2): anyone reads, Admin and Support change.
 */
public final class PlatformMatrices {

    public static final AuthorizationMatrix REFERENCE_DATA = AuthorizationMatrix.create()
            .allowEveryone(GET_BY_ID)
            .allowEveryone(GET_ALL)
            .allow(CREATE, ADMIN, SUPPORT)
            .allow(UPDATE, ADMIN, SUPPORT)
            .allow(DISABLE, ADMIN, SUPPORT)
            .allow(DELETE, ADMIN, SUPPORT);

    private PlatformMatrices() {
    }
}
