package com.triphuddle.api.group.security;

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
 * What a caller outside the group may do, by platform role. Members and group admins get more, which each policy
 * test checks on its own; nobody outside the group may delete.
 */
public final class GroupMatrices {

    /** Any signed-in user creates a group; staff read and change any group. */
    public static final AuthorizationMatrix GROUPS = AuthorizationMatrix.create()
            .allow(CREATE, CUSTOMER, SALES, INSPECTOR, SUPPORT, ADMIN)
            .allow(GET_BY_ID, ADMIN, SUPPORT, INSPECTOR)
            .allow(GET_ALL, ADMIN, SUPPORT, INSPECTOR)
            .allow(UPDATE, ADMIN, SUPPORT)
            .allow(DISABLE, ADMIN, SUPPORT);

    /** Memberships and invitations: Admin and Support manage them, Inspector reads them. */
    public static final AuthorizationMatrix GROUP_PARTS = AuthorizationMatrix.create()
            .allow(CREATE, ADMIN, SUPPORT)
            .allow(GET_BY_ID, ADMIN, SUPPORT, INSPECTOR)
            .allow(GET_ALL, ADMIN, SUPPORT, INSPECTOR)
            .allow(UPDATE, ADMIN, SUPPORT)
            .allow(DISABLE, ADMIN, SUPPORT);

    private GroupMatrices() {
    }
}
