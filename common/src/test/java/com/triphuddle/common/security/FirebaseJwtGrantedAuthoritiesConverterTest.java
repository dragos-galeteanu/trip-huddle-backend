package com.triphuddle.common.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Locale;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class FirebaseJwtGrantedAuthoritiesConverterTest {

    private final FirebaseJwtGrantedAuthoritiesConverter converter = new FirebaseJwtGrantedAuthoritiesConverter();

    @ParameterizedTest(name = "role \"{0}\" becomes {1}")
    @CsvSource({"Admin, ROLE_ADMIN", "support, ROLE_SUPPORT", "ROLE_Inspector, ROLE_INSPECTOR"})
    void turnsTheRoleClaimIntoARoleAuthority(String role, String authority) {
        assertThat(authorities(jwt -> jwt.claim("role", role))).contains(authority);
    }

    @Test
    void keepsScopesAlongsideTheRole() {
        assertThat(authorities(jwt -> jwt.claim("role", "Admin").claim("scope", "read write")))
                .containsExactlyInAnyOrder("ROLE_ADMIN", "SCOPE_read", "SCOPE_write");
    }

    @Test
    void addsNoRoleWhenTheClaimIsMissingOrBlank() {
        assertThat(authorities(jwt -> jwt.claim("other", "x"))).isEmpty();
        assertThat(authorities(jwt -> jwt.claim("role", " "))).isEmpty();
    }

    @Test
    @ResourceLock("java.util.Locale")
    void isUnaffectedByATurkishDefaultLocale() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(authorities(jwt -> jwt.claim("role", "admin"))).containsExactly("ROLE_ADMIN");
        } finally {
            Locale.setDefault(before);
        }
    }

    private java.util.List<String> authorities(Consumer<Jwt.Builder> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none").subject("s");
        claims.accept(builder);
        return converter.convert(builder.build()).stream().map(GrantedAuthority::getAuthority).toList();
    }
}
