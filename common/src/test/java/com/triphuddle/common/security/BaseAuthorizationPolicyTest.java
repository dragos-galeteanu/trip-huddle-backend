package com.triphuddle.common.security;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class BaseAuthorizationPolicyTest {

    private static final UUID OWNER = UUID.randomUUID();

    /** A policy whose resources are all owned by {@link #OWNER}; only its default methods are under test. */
    private final BaseAuthorizationPolicy<Object, Object, UUID> policy = new BaseAuthorizationPolicy<>() {
        @Override public boolean canCreate(Authentication a, Object r) { return false; }
        @Override public boolean canGetById(Authentication a, UUID id) { return false; }
        @Override public boolean canGetAll(Authentication a) { return false; }
        @Override public boolean canUpdate(Authentication a, Object r) { return false; }
        @Override public boolean canDelete(Authentication a, UUID id) { return false; }
        @Override public Optional<UUID> resolveOwnerUserId(UUID id) { return Optional.of(OWNER); }
    };

    @Nested
    class Roles {

        @Test
        void grantsWhenAnyAuthorityMatches() {
            Authentication support = withAuthorities("ROLE_SUPPORT");

            assertThat(policy.isAdminOrSupport(support)).isTrue();
            assertThat(policy.isAdminOrSupportOrInspector(support)).isTrue();
            assertThat(policy.isSupport(support)).isTrue();
            assertThat(policy.isAdmin(support)).isFalse();
            assertThat(policy.canDisable(support)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"ROLE_ADMIN", "role_admin", "Admin", "ADMIN"})
        void readsAuthoritiesWithOrWithoutThePrefixAndInAnyCase(String authority) {
            assertThat(policy.isAdmin(withAuthorities(authority))).isTrue();
        }

        @Test
        void neverTakesAScopeForARole() {
            assertThat(policy.isAdmin(withAuthorities("SCOPE_ADMIN"))).isFalse();
        }

        @Test
        void eachSingleRoleCheckMatchesOnlyItsRole() {
            assertThat(policy.isInspector(withAuthorities("ROLE_INSPECTOR"))).isTrue();
            assertThat(policy.isSales(withAuthorities("ROLE_SALES"))).isTrue();
            assertThat(policy.isSales(withAuthorities("ROLE_INSPECTOR"))).isFalse();
            assertThat(policy.isAdminOrSupportOrInspector(withAuthorities("ROLE_SALES"))).isFalse();
        }

        @Test
        void refusesWithoutAuthenticationOrAuthorities() {
            assertThat(policy.hasAnyAuthority(null, Set.of(UserRole.Admin))).isFalse();
            assertThat(policy.hasAnyAuthority(new TestingAuthenticationToken("u", "p", List.of()) {
                @Override
                public java.util.Collection<org.springframework.security.core.GrantedAuthority> getAuthorities() {
                    return null;
                }
            }, Set.of(UserRole.Admin))).isFalse();
            assertThat(policy.normalizeRoleAuthority(null)).isEmpty();
        }
    }

    @Nested
    class Authenticated {

        @Test
        void acceptsASignedInUser() {
            assertThat(policy.isAuthenticated(jwtFor(jwt -> jwt.claim("userId", OWNER.toString())))).isTrue();
        }

        @Test
        void refusesAnAnonymousTokenEvenThoughItSaysItIsAuthenticated() {
            Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                    AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

            assertThat(anonymous.isAuthenticated()).isTrue();
            assertThat(policy.isAuthenticated(anonymous)).isFalse();
            assertThat(policy.isAuthenticated(null)).isFalse();
        }
    }

    @Nested
    class CurrentUser {

        @Test
        void matchesTheUserIdClaim() {
            assertThat(policy.isCurrentUser(jwtFor(jwt -> jwt.claim("userId", OWNER.toString())), OWNER)).isTrue();
            assertThat(policy.isCurrentUser(jwtFor(jwt -> jwt.claim("userId", OWNER.toString())),
                    UUID.randomUUID())).isFalse();
        }

        @Test
        void fallsBackToTheSubjectWithoutAUserIdClaim() {
            assertThat(policy.isCurrentUser(jwtFor(jwt -> jwt.subject(OWNER.toString())), OWNER)).isTrue();
            assertThat(policy.isCurrentUser(jwtFor(jwt -> jwt.claim("userId", " ").subject(OWNER.toString())),
                    OWNER)).isTrue();
        }

        @Test
        void refusesMalformedOrMissingIdentities() {
            assertThat(policy.isCurrentUser(jwtFor(jwt -> jwt.claim("userId", "not-a-uuid")), OWNER)).isFalse();
            assertThat(policy.isCurrentUser(jwtFor(jwt -> jwt.subject("firebase-uid")), OWNER)).isFalse();
            assertThat(policy.isCurrentUser(jwtFor(jwt -> jwt.claim("other", "x")), OWNER)).isFalse();
            assertThat(policy.isCurrentUser(jwtFor(jwt -> jwt.subject(" ")), OWNER)).isFalse();
            assertThat(policy.isCurrentUser(null, OWNER)).isFalse();
            assertThat(policy.isCurrentUser(jwtFor(jwt -> jwt.subject(OWNER.toString())), null)).isFalse();
        }

        @Test
        void refusesAPrincipalThatIsNotAJwt() {
            assertThat(policy.isCurrentUser(new TestingAuthenticationToken(OWNER.toString(), "p"), OWNER)).isFalse();
            assertThat(policy.isCurrentUser(new TestingAuthenticationToken(null, "p"), OWNER)).isFalse();
        }
    }

    @Nested
    class ResourceOwner {

        @Test
        void isTheUserTheResourceResolvesTo() {
            assertThat(policy.isResourceOwner(jwtFor(jwt -> jwt.claim("userId", OWNER.toString())),
                    UUID.randomUUID())).isTrue();
            assertThat(policy.isResourceOwner(jwtFor(jwt -> jwt.claim("userId", UUID.randomUUID().toString())),
                    UUID.randomUUID())).isFalse();
        }

        @Test
        void isNobodyWhenNoOwnerResolvesOrNothingIsGiven() {
            BaseAuthorizationPolicy<Object, Object, UUID> ownerless = new BaseAuthorizationPolicy<>() {
                @Override public boolean canCreate(Authentication a, Object r) { return false; }
                @Override public boolean canGetById(Authentication a, UUID id) { return false; }
                @Override public boolean canGetAll(Authentication a) { return false; }
                @Override public boolean canUpdate(Authentication a, Object r) { return false; }
                @Override public boolean canDelete(Authentication a, UUID id) { return false; }
            };
            Authentication owner = jwtFor(jwt -> jwt.claim("userId", OWNER.toString()));

            assertThat(ownerless.isResourceOwner(owner, UUID.randomUUID())).isFalse();
            assertThat(policy.isResourceOwner(null, UUID.randomUUID())).isFalse();
            assertThat(policy.isResourceOwner(owner, null)).isFalse();
        }
    }

    private static Authentication withAuthorities(String... authorities) {
        return new TestingAuthenticationToken("user", "password", authorities);
    }

    private static Authentication jwtFor(Consumer<Jwt.Builder> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none");
        claims.accept(builder);
        return new JwtAuthenticationToken(builder.build(), List.of());
    }
}
