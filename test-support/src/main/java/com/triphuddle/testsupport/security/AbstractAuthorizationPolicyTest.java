package com.triphuddle.testsupport.security;

import com.triphuddle.common.security.BaseAuthorizationPolicy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks a policy against its matrix for every operation and caller, allowed and refused alike.
 *
 * @param <C> the create request
 * @param <U> the update request
 * @param <P> the policy
 */
public abstract class AbstractAuthorizationPolicyTest<C, U, P extends BaseAuthorizationPolicy<C, U, UUID>> {

    protected abstract P policy();

    protected abstract AuthorizationMatrix matrix();

    protected abstract C createRequest();

    protected abstract U updateRequest();

    @ParameterizedTest(name = "{0} by {1}")
    @MethodSource("com.triphuddle.testsupport.security.AuthorizationMatrix#everyOperationAndCaller")
    void answersAsTheMatrixSays(Operation operation, Caller caller) {
        assertThat(decide(operation, caller.authentication()))
                .as("%s by %s", operation, caller)
                .isEqualTo(matrix().isAllowed(operation, caller));
    }

    @ParameterizedTest(name = "{0} without authentication")
    @EnumSource(Operation.class)
    void treatsNoAuthenticationLikeAnAnonymousCaller(Operation operation) {
        assertThat(decide(operation, null)).as(operation.name())
                .isEqualTo(matrix().isAllowed(operation, Caller.ANONYMOUS));
    }

    private boolean decide(Operation operation, Authentication authentication) {
        UUID id = UUID.randomUUID();
        return switch (operation) {
            case CREATE -> policy().canCreate(authentication, createRequest());
            case GET_BY_ID -> policy().canGetById(authentication, id);
            case GET_ALL -> policy().canGetAll(authentication);
            case UPDATE -> policy().canUpdate(authentication, updateRequest());
            case DISABLE -> policy().canDisable(authentication);
            case DELETE -> policy().canDelete(authentication, id);
        };
    }
}
