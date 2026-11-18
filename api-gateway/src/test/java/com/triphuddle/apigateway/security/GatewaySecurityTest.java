package com.triphuddle.apigateway.security;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The gateway reads Firebase's role claim the way the services do, so both check the same authorities. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class GatewaySecurityTest {

    static Jwt token(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none").subject("user");
        claims.forEach(builder::claim);
        return builder.build();
    }

    @Test
    void the_role_claim_becomes_one_role_authority_alongside_the_scopes() {
        FirebaseJwtGrantedAuthoritiesConverter converter = new FirebaseJwtGrantedAuthoritiesConverter();

        assertThat(converter.convert(token(Map.of("role", "Admin", "scope", "read"))))
                .extracting(GrantedAuthority::getAuthority).containsExactlyInAnyOrder("ROLE_ADMIN", "SCOPE_read");
        assertThat(converter.convert(token(Map.of("role", "role_support"))))
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_SUPPORT");
        assertThat(converter.convert(token(Map.of("role", " ")))).isEmpty();
        assertThat(converter.convert(token(Map.of("email", "a@example.com")))).isEmpty();
    }

    @Test
    void the_reactive_converter_hands_those_authorities_to_spring_security() {
        AbstractAuthenticationToken authentication = new SecurityConfig().reactiveJwtAuthenticationConverter()
                .convert(token(Map.of("role", "Customer"))).block();

        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CUSTOMER");
    }
}
