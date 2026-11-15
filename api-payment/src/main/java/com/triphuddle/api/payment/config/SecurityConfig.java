package com.triphuddle.api.payment.config;

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
 * Security for the payment service: the Stripe webhook is public, as its signature is the proof; the rest needs
 * a token.
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
     * Stripe calls the webhook without a token; {@code StripeWebhooks} checks its signature instead.
     * @return the request matchers permitted without authentication
     */
    @Override
    public List<RequestMatcher> additionalPermitAllMatchers() {
        return List.of(AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/api/v1/payments/webhook"));
    }
}
