package com.triphuddle.common.security;

import com.triphuddle.common.config.StompDestinations;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class BaseStompAuthChannelInterceptorTest {

    private static final UUID USER = UUID.randomUUID();
    private static final MessageChannel CHANNEL = mock(MessageChannel.class);

    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final BaseStompAuthChannelInterceptor interceptor =
            new BaseStompAuthChannelInterceptor(decoder, new JwtAuthenticationConverter());

    @Nested
    class Connect {

        @Test
        void authenticatesTheSessionFromTheBearerToken() {
            given(decoder.decode("good-token")).willReturn(jwt(USER, Instant.now().plusSeconds(60)));

            Message<?> message = interceptor.preSend(frame(StompCommand.CONNECT,
                    a -> a.setNativeHeader("Authorization", "Bearer good-token ")), CHANNEL);

            Authentication user = (Authentication) accessorOf(message).getUser();
            assertThat(SecurityUtils.extractUserId(user)).isEqualTo(USER);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"good-token", "Basic abc"})
        void refusesAMissingOrNonBearerHeader(String header) {
            assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT,
                    a -> a.setNativeHeader("Authorization", header)), CHANNEL))
                    .isInstanceOf(MessageDeliveryException.class)
                    .hasMessageContaining("Bearer");
        }

        @Test
        void refusesAnInvalidToken() {
            given(decoder.decode("bad")).willThrow(new BadJwtException("expired"));

            assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT,
                    a -> a.setNativeHeader("Authorization", "Bearer bad")), CHANNEL))
                    .isInstanceOf(MessageDeliveryException.class)
                    .hasMessage("Invalid token");
        }

        @Test
        void refusesATokenWithoutAPlatformUserId() {
            given(decoder.decode("unregistered")).willReturn(Jwt.withTokenValue("t").header("alg", "none")
                    .subject("firebase-uid").build());

            assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT,
                    a -> a.setNativeHeader("Authorization", "Bearer unregistered")), CHANNEL))
                    .isInstanceOf(MessageDeliveryException.class)
                    .hasMessage("Invalid token");
        }
    }

    @Nested
    class Subscribe {

        @Test
        void allowsTheUsersOwnTopic() {
            Message<?> message = frame(StompCommand.SUBSCRIBE, a -> {
                a.setUser(session(USER, Instant.now().plusSeconds(60)));
                a.setDestination(StompDestinations.userTopic(USER));
            });

            assertThat(interceptor.preSend(message, CHANNEL)).isSameAs(message);
        }

        @Test
        void refusesAnotherUsersTopic() {
            assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, a -> {
                a.setUser(session(USER, null));
                a.setDestination(StompDestinations.userTopic(UUID.randomUUID()));
            }), CHANNEL)).isInstanceOf(MessageDeliveryException.class).hasMessageContaining("cannot subscribe");
        }

        @Test
        void letsAServiceWidenWhatMayBeSubscribed() {
            BaseStompAuthChannelInterceptor withGroups = new BaseStompAuthChannelInterceptor(decoder,
                    new JwtAuthenticationConverter()) {
                @Override
                protected boolean maySubscribe(UUID userId, String destination) {
                    return destination.startsWith("/topic/group.");
                }
            };

            assertThat(withGroups.preSend(frame(StompCommand.SUBSCRIBE, a -> {
                a.setUser(session(USER, null));
                a.setDestination("/topic/group.42");
            }), CHANNEL)).isNotNull();
        }

        @Test
        void refusesASessionThatNeverConnectedOrWhoseTokenExpired() {
            assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE,
                    a -> a.setDestination(StompDestinations.userTopic(USER))), CHANNEL))
                    .hasMessage("Not authenticated");
            assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, a -> {
                a.setUser(new TestingAuthenticationToken("someone", "p"));
                a.setDestination(StompDestinations.userTopic(USER));
            }), CHANNEL)).hasMessage("Not authenticated");
            assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, a -> {
                a.setUser(session(USER, Instant.now().minusSeconds(1)));
                a.setDestination(StompDestinations.userTopic(USER));
            }), CHANNEL)).hasMessageContaining("Token expired");
        }
    }

    @Nested
    class Send {

        @Test
        void allowsTheApplicationPrefix() {
            Message<?> message = frame(StompCommand.SEND, a -> {
                a.setUser(session(USER, null));
                a.setDestination("/app/chat.send");
            });

            assertThat(interceptor.preSend(message, CHANNEL)).isSameAs(message);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"/topic/user.someone", "/application/x", "/app"})
        void refusesAnyOtherDestination(String destination) {
            assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, a -> {
                a.setUser(session(USER, null));
                a.setDestination(destination);
            }), CHANNEL)).isInstanceOf(MessageDeliveryException.class).hasMessageContaining("/app/**");
        }
    }

    @ParameterizedTest
    @EnumSource(value = StompCommand.class, names = {"UNSUBSCRIBE", "DISCONNECT", "ACK", "NACK"})
    void letsOtherCommandsThroughUnchecked(StompCommand command) {
        Message<?> message = frame(command, a -> { });

        assertThat(interceptor.preSend(message, CHANNEL)).isSameAs(message);
    }

    @Test
    void letsFramesWithoutACommandThrough() {
        Message<byte[]> heartbeat = MessageBuilder.withPayload(new byte[0]).build();

        assertThat(interceptor.preSend(heartbeat, CHANNEL)).isSameAs(heartbeat);
    }

    private static Message<?> frame(StompCommand command, Consumer<StompHeaderAccessor> headers) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId("session-1");
        headers.accept(accessor);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static StompHeaderAccessor accessorOf(Message<?> message) {
        return MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    }

    private static Jwt jwt(UUID userId, Instant expiresAt) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none").claim("userId", userId.toString());
        if (expiresAt != null) {
            builder.issuedAt(expiresAt.minusSeconds(3600)).expiresAt(expiresAt);
        }
        return builder.build();
    }

    private static Authentication session(UUID userId, Instant expiresAt) {
        return new JwtAuthenticationToken(jwt(userId, expiresAt), List.of());
    }
}
