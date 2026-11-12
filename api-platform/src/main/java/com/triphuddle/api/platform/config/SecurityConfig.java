package com.triphuddle.api.platform.config;

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

@Configuration
@EnableMethodSecurity
public class SecurityConfig implements BaseSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return BaseSecurityConfig.super.filterChain(http);
    }

    @Bean
    public Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return BaseSecurityConfig.super.jwtAuthenticationConverter();
    }

    /**
     * Reference data is public to read: GET on countries, currencies and languages needs no token.
     * @return the request matchers permitted without authentication
     */
    @Override
    public List<RequestMatcher> additionalPermitAllMatchers() {
        return List.of(
                AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/platform/countries/**"),
                AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/platform/currencies/**"),
                AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/v1/platform/languages/**")
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
