package com.triphuddle.api.user.security;

import com.triphuddle.api.user.dto.RegisterRequest;
import com.triphuddle.api.user.dto.UpdateUserRequest;
import com.triphuddle.api.user.fixtures.UserFixtures;
import com.triphuddle.testsupport.security.AbstractAuthorizationPolicyTest;
import com.triphuddle.testsupport.security.AuthorizationMatrix;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserAuthorizationPolicyTest
        extends AbstractAuthorizationPolicyTest<RegisterRequest, UpdateUserRequest, UserAuthorizationPolicy> {

    @Override
    protected UserAuthorizationPolicy policy() {
        return new UserAuthorizationPolicy();
    }

    @Override
    protected AuthorizationMatrix matrix() {
        return UserMatrices.USERS;
    }

    @Override
    protected RegisterRequest createRequest() {
        return UserFixtures.register().build();
    }

    @Override
    protected UpdateUserRequest updateRequest() {
        return UserFixtures.update(UserFixtures.user()).build();
    }

    @Test
    void usersChangeAndDeleteOnlyThemselves() {
        UUID me = UUID.randomUUID();
        UpdateUserRequest myUpdate = UpdateUserRequest.builder().id(me).build();

        assertThat(policy().canUpdate(Caller.CUSTOMER.authentication(me), myUpdate)).isTrue();
        assertThat(policy().canDelete(Caller.CUSTOMER.authentication(me), me)).isTrue();
        assertThat(policy().canDelete(Caller.ADMIN.authentication(), me)).as("not even an admin deletes another")
                .isFalse();
    }
}
