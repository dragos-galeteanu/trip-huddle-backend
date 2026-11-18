package com.triphuddle.api.user.security;

import com.triphuddle.testsupport.security.AuthorizationMatrix;

import static com.triphuddle.testsupport.security.Caller.ADMIN;
import static com.triphuddle.testsupport.security.Caller.CUSTOMER;
import static com.triphuddle.testsupport.security.Caller.INSPECTOR;
import static com.triphuddle.testsupport.security.Caller.SALES;
import static com.triphuddle.testsupport.security.Caller.SUPPORT;
import static com.triphuddle.testsupport.security.Operation.CREATE;
import static com.triphuddle.testsupport.security.Operation.DISABLE;
import static com.triphuddle.testsupport.security.Operation.GET_ALL;
import static com.triphuddle.testsupport.security.Operation.GET_BY_ID;
import static com.triphuddle.testsupport.security.Operation.UPDATE;

/**
 * What a caller may do to someone else's user or profile, by role. Users and profiles are a directory any signed-in
 * user reads (names and public profile fields only); the owner changes their own, which the policy tests check.
 * Over HTTP nobody gets in without a token: this service turns anonymous authentication off.
 */
public final class UserMatrices {

    public static final AuthorizationMatrix USERS = AuthorizationMatrix.create()
            .allowEveryone(GET_BY_ID)
            .allowEveryone(GET_ALL)
            .allow(UPDATE, ADMIN, SUPPORT)
            .allow(DISABLE, ADMIN, SUPPORT);

    public static final AuthorizationMatrix PROFILES = AuthorizationMatrix.create()
            .allow(CREATE, ADMIN, SUPPORT)
            .allowEveryone(GET_BY_ID)
            .allowEveryone(GET_ALL)
            .allow(UPDATE, ADMIN, SUPPORT)
            .allow(DISABLE, ADMIN, SUPPORT);

    public static final AuthorizationMatrix PROFILES_OVER_HTTP = AuthorizationMatrix.create()
            .allow(CREATE, ADMIN, SUPPORT)
            .allow(GET_BY_ID, CUSTOMER, SALES, INSPECTOR, SUPPORT, ADMIN)
            .allow(GET_ALL, CUSTOMER, SALES, INSPECTOR, SUPPORT, ADMIN)
            .allow(UPDATE, ADMIN, SUPPORT)
            .allow(DISABLE, ADMIN, SUPPORT);

    private UserMatrices() {
    }
}
