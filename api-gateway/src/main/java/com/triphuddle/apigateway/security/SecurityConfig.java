package com.triphuddle.apigateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private List<String> allowedOrigins;

    // Default false — set app.swagger.public=true only in local dev profile
    @Value("${app.swagger.public:false}")
    private boolean swaggerPublic;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(corsSpec -> corsSpec.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchanges -> {
                    exchanges.pathMatchers("/actuator/health", "/fallback").permitAll();
                    if (swaggerPublic) {
                        exchanges.pathMatchers("/*/v3/api-docs", "/*/swagger-ui/**").permitAll();
                    } else {
                        exchanges.pathMatchers("/*/v3/api-docs", "/*/swagger-ui/**").authenticated();
                    }
                    exchanges
                            .pathMatchers("/api/v1/payments/webhook", "/api/v1/payments/webhook/**").permitAll()
                            .pathMatchers("/api/v1/users/verify-email").permitAll()
                            .pathMatchers("/api/v1/users/forgot-password").permitAll()
                            .pathMatchers(HttpMethod.GET,
                                    "/api/v1/platform/countries/**",
                                    "/api/v1/platform/currencies/**",
                                    "/api/v1/platform/languages/**").permitAll()
                            .pathMatchers("/api/**").authenticated()
                            .anyExchange().authenticated();
                })
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        reactiveJwtAuthenticationConverter())))
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public ReactiveJwtAuthenticationConverter reactiveJwtAuthenticationConverter() {
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt ->
                Flux.fromIterable(Objects.requireNonNull(new FirebaseJwtGrantedAuthoritiesConverter().convert(jwt)))
        );
        return converter;
    }
}
