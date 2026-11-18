package com.triphuddle.common.config;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.SimpleBrokerRegistration;
import org.springframework.messaging.simp.config.StompBrokerRelayRegistration;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

class BaseConfigTest {

    @Nested
    class OpenApi {

        @Test
        void describesTheServiceAndRequiresABearerToken() {
            OpenAPI api = ((BaseOpenApiConfig) new BaseOpenApiConfig() {
                @Override public String title() { return "Platform API"; }
                @Override public String description() { return "Reference data"; }
            }).openApi();

            assertThat(api.getInfo().getTitle()).isEqualTo("Platform API");
            assertThat(api.getInfo().getVersion()).isEqualTo("v1");
            assertThat(api.getSecurity()).singleElement().satisfies(r -> assertThat(r).containsKey("bearerAuth"));
            SecurityScheme scheme = api.getComponents().getSecuritySchemes().get("bearerAuth");
            assertThat(scheme.getScheme()).isEqualTo("bearer");
            assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
        }
    }

    @Nested
    class WebSocket {

        private final ChannelInterceptor interceptor = mock(ChannelInterceptor.class);

        private BaseWebSocketConfig config(boolean relay, List<ChannelInterceptor> interceptors) {
            return new BaseWebSocketConfig() {
                @Override public String endpoint() { return "/ws"; }
                @Override public List<String> allowedOrigins() { return List.of("https://app.example"); }
                @Override public StompBrokerProperties broker() {
                    return new StompBrokerProperties(relay, "rabbit", 61613, "guest", "secret", "/");
                }
                @Override public List<ChannelInterceptor> inboundInterceptors() { return interceptors; }
            };
        }

        @Test
        void registersTheEndpointForTheAllowedOrigins() {
            StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
            StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
            given(registry.addEndpoint("/ws")).willReturn(registration);

            config(false, List.of()).registerStompEndpoints(registry);

            then(registration).should().setAllowedOriginPatterns("https://app.example");
        }

        @Test
        void relaysToRabbitWhenTheRelayIsOn() {
            MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);
            StompBrokerRelayRegistration relay = mock(StompBrokerRelayRegistration.class, RETURNS_SELF);
            given(registry.enableStompBrokerRelay("/topic")).willReturn(relay);

            config(true, List.of()).configureMessageBroker(registry);

            then(registry).should().setApplicationDestinationPrefixes("/app");
            then(relay).should().setRelayHost("rabbit");
            then(relay).should().setRelayPort(61613);
            then(relay).should().setSystemPasscode("secret");
            then(registry).should(never()).enableSimpleBroker(any(String[].class));
        }

        @Test
        void usesTheInMemoryBrokerOtherwise() {
            MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);
            given(registry.enableSimpleBroker("/topic")).willReturn(mock(SimpleBrokerRegistration.class));

            config(false, List.of()).configureMessageBroker(registry);

            then(registry).should().enableSimpleBroker("/topic");
            then(registry).should(never()).enableStompBrokerRelay(any(String[].class));
        }

        @Test
        void addsInboundInterceptorsOnlyWhenThereAreSome() {
            ChannelRegistration with = mock(ChannelRegistration.class);
            ChannelRegistration without = mock(ChannelRegistration.class);

            config(false, List.of(interceptor)).configureClientInboundChannel(with);
            config(false, List.of()).configureClientInboundChannel(without);

            then(with).should().interceptors(interceptor);
            then(without).shouldHaveNoInteractions();
        }

        @Test
        void namesEachUsersTopicWithDotsForRabbit() {
            UUID user = UUID.randomUUID();

            assertThat(StompDestinations.userTopic(user)).isEqualTo("/topic/user." + user);
        }
    }

    @Nested
    class Storage_ {

        @Test
        void buildsAClientForTheConfiguredProject() throws Exception {
            GoogleCredentials credentials = GoogleCredentials.create(new AccessToken("token", null));
            StorageBucketProperties properties = new StorageBucketProperties("project-1", "bucket-1", 10, 15, 60);
            try (MockedStatic<GoogleCredentials> google = mockStatic(GoogleCredentials.class)) {
                google.when(GoogleCredentials::getApplicationDefault).thenReturn(credentials);

                Storage storage = new BaseStorageConfig() { }.storage(properties);

                assertThat(storage.getOptions().getProjectId()).isEqualTo("project-1");
            }
        }

        @Test
        void isEnabledOnlyWithABucket() {
            assertThat(new StorageBucketProperties("p", "bucket", 1, 1, 1).isEnabled()).isTrue();
            assertThat(new StorageBucketProperties("p", " ", 1, 1, 1).isEnabled()).isFalse();
            assertThat(new StorageBucketProperties("p", null, 1, 1, 1).isEnabled()).isFalse();
        }
    }
}
