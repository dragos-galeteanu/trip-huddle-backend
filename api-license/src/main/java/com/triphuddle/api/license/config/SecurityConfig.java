package com.triphuddle.api.license.config;

import com.triphuddle.common.security.BaseSecurityConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;

/**
 * Security for the license service: the license catalog is public to read, everything else needs a token.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig implements BaseSecurityConfig {

    /**
     * Registers the security filter chain as a bean.
     *
     * @param http the HttpSecurity object
     * @return the configured SecurityFilterChain
     * @throws Exception if an error occurs during configuration
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return BaseSecurityConfig.super.filterChain(http);
    }

    /**
     * Registers the converter that reads Firebase roles from the JWT as a bean.
     *
     * @return a converter that converts a JWT to an AbstractAuthenticationToken
     */
    @Bean
    public Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return BaseSecurityConfig.super.jwtAuthenticationConverter();
    }

    /**
     * The catalog is public to read: GET on the license list and on one license needs no token.
     * @return the request matchers permitted without authentication
     */
    @Override
    public List<RequestMatcher> additionalPermitAllMatchers() {
        return List.of(
                // One segment only, so deeper routes stay private; the policies still guard what matches.
                AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/licenses"),
                AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/licenses/*"),
                AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/licenses/*/price")
        );
    }

    /**
     * Keeps anonymous authentication, as the public GET routes are still guarded by {@code @PreAuthorize}.
     * @return true
     */
    @Override
    public boolean anonymousAuthenticationEnabled() {
        // Without an Authentication, method security rejects the call before the policy is asked.
        return true;
    }
}
