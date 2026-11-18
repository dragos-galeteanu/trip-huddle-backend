package com.triphuddle.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityUtilsTest {

    private static final UUID USER = UUID.randomUUID();

    @Test
    void readsTheUserIdClaimFirst() {
        assertThat(SecurityUtils.extractUserId(jwt(j -> j.claim("userId", USER.toString()).subject("firebase-uid"))))
                .isEqualTo(USER);
    }

    @Test
    void fallsBackToTheSubjectWhenTheClaimIsMissingBlankOrMalformed() {
        assertThat(SecurityUtils.extractUserId(jwt(j -> j.subject(USER.toString())))).isEqualTo(USER);
        assertThat(SecurityUtils.extractUserId(jwt(j -> j.claim("userId", " ").subject(USER.toString()))))
                .isEqualTo(USER);
        assertThat(SecurityUtils.extractUserId(jwt(j -> j.claim("userId", "nope").subject(USER.toString()))))
                .isEqualTo(USER);
    }

    @Test
    void refusesATokenWithoutAPlatformUserId() {
        assertThatThrownBy(() -> SecurityUtils.extractUserId(jwt(j -> j.subject("firebase-uid"))))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("complete user registration first");
        assertThatThrownBy(() -> SecurityUtils.extractUserId(jwt(j -> j.subject(" "))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void refusesAMissingOrNonJwtAuthentication() {
        assertThatThrownBy(() -> SecurityUtils.extractUserId(null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> SecurityUtils.extractUserId(new TestingAuthenticationToken(USER.toString(), "p")))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static Authentication jwt(Consumer<Jwt.Builder> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none").claim("iss", "test");
        claims.accept(builder);
        return new JwtAuthenticationToken(builder.build(), List.of());
    }
}
