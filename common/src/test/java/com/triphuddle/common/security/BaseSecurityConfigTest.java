package com.triphuddle.common.security;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BaseSecurityConfigTest {

    @RestController
    static class Probe {

        @GetMapping({"/actuator/health", "/api-docs/x", "/swagger-ui.html", "/public/x", "/private/x"})
        String who(@CurrentSecurityContext SecurityContext context) {
            Authentication authentication = context.getAuthentication();
            return authentication == null ? "none" : authentication.getClass().getSimpleName()
                    + authentication.getAuthorities();
        }

        @org.springframework.web.bind.annotation.PostMapping("/private/x")
        String write() {
            return "written";
        }
    }

    static MockMvc mvc(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class Closed implements BaseSecurityConfig {

        @Bean
        SecurityFilterChain chain(HttpSecurity http) throws Exception {
            return filterChain(http);
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }

        @Bean
        Probe probe() {
            return new Probe();
        }
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class OpenReads extends Closed {

        @Override
        public List<RequestMatcher> additionalPermitAllMatchers() {
            return List.of(AntPathRequestMatcher.antMatcher("/public/**"));
        }

        @Override
        public boolean anonymousAuthenticationEnabled() {
            return true;
        }
    }

    @Nested
    @SpringJUnitWebConfig(Closed.class)
    class ByDefault {

        @Autowired
        WebApplicationContext context;

        @Test
        void letsHealthAndApiDocsThroughWithoutAToken() throws Exception {
            for (String path : List.of("/actuator/health", "/api-docs/x", "/swagger-ui.html")) {
                mvc(context).perform(get(path)).andExpect(status().isOk());
            }
        }

        @Test
        void refusesEverythingElseWithoutAToken() throws Exception {
            mvc(context).perform(get("/private/x")).andExpect(status().isUnauthorized());
            mvc(context).perform(get("/public/x")).andExpect(status().isUnauthorized());
        }

        @Test
        void mapsTheRoleClaimOfAValidTokenAndNeedsNoCsrfToken() throws Exception {
            mvc(context).perform(get("/private/x").with(jwt().jwt(j -> j.claim("role", "Admin"))
                            .authorities(new FirebaseJwtGrantedAuthoritiesConverter())))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("ROLE_ADMIN")));
            mvc(context).perform(post("/private/x").with(jwt())).andExpect(status().isOk());
        }

        @Test
        void hasNoAnonymousAuthentication() throws Exception {
            mvc(context).perform(get("/actuator/health")).andExpect(content().string("none"));
        }

        @Test
        void convertsTheRoleClaimWithTheFirebaseConverter() {
            Jwt token = Jwt.withTokenValue("t").header("alg", "none").claim("role", "support").build();

            org.assertj.core.api.Assertions.assertThat(new Closed().jwtAuthenticationConverter().convert(token)
                    .getAuthorities()).extracting(Object::toString).contains("ROLE_SUPPORT");
        }
    }

    @Nested
    @SpringJUnitWebConfig(OpenReads.class)
    class WithPublicRoutesAndAnonymousAuthentication {

        @Autowired
        WebApplicationContext context;

        @Test
        void letsThePublicRoutesThroughAsAnAnonymousCaller() throws Exception {
            mvc(context).perform(get("/public/x"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("AnonymousAuthenticationToken[ROLE_ANONYMOUS]"));
        }

        @Test
        void stillRefusesEveryOtherRouteToAnAnonymousCaller() throws Exception {
            mvc(context).perform(get("/private/x")).andExpect(status().isUnauthorized());
        }
    }
}
