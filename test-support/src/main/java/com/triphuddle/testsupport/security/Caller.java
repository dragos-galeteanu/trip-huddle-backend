package com.triphuddle.testsupport.security;

import com.triphuddle.common.security.FirebaseJwtGrantedAuthoritiesConverter;
import com.triphuddle.common.security.UserRole;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Who makes a call: nobody, or a signed-in user with one role. Authorities come from the real
 * {@link FirebaseJwtGrantedAuthoritiesConverter}, so role parsing is part of every test that uses a caller.
 */
public enum Caller {
    ANONYMOUS(null),
    CUSTOMER(UserRole.Customer),
    SALES(UserRole.Sales),
    INSPECTOR(UserRole.Inspector),
    SUPPORT(UserRole.Support),
    ADMIN(UserRole.Admin);

    private static final FirebaseJwtGrantedAuthoritiesConverter CONVERTER = new FirebaseJwtGrantedAuthoritiesConverter();

    private final UserRole role;

    Caller(UserRole role) {
        this.role = role;
    }

    /**
     * The caller as Spring Security sees it inside the application, as some user.
     */
    public Authentication authentication(UUID userId) {
        if (role == null) {
            return new AnonymousAuthenticationToken("test", "anonymousUser",
                    AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        }
        Jwt token = token(userId);
        return new JwtAuthenticationToken(token, CONVERTER.convert(token), userId.toString());
    }

    public Authentication authentication() {
        return authentication(UUID.randomUUID());
    }

    /**
     * The caller on a MockMvc request, as some user; anonymous requests carry no token at all.
     */
    public RequestPostProcessor request(UUID userId) {
        if (role == null) {
            return request -> request;
        }
        return jwt().jwt(token(userId)).authorities(CONVERTER);
    }

    public RequestPostProcessor request() {
        return request(UUID.randomUUID());
    }

    private Jwt token(UUID userId) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("userId", userId.toString())
                .claim("role", role.name())
                .build();
    }
}
