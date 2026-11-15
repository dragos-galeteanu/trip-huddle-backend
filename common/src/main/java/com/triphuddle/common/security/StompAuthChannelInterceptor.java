package com.triphuddle.common.security;

import com.triphuddle.common.config.StompDestinations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.UUID;

/**
 * Authenticates a STOMP session from the Firebase token in its CONNECT frame and guards what it may do.
 * A session may only subscribe to its own user topic and only send to application destinations, unless overridden.
 */
@Slf4j
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter;

    public StompAuthChannelInterceptor(
            JwtDecoder jwtDecoder,
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter) {
        this.jwtDecoder = jwtDecoder;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }

    /**
     * Whether a user may subscribe to a destination.
     *
     * @param userId the ID of the user
     * @param destination the destination of the SUBSCRIBE frame, possibly null
     * @return true if the subscription is allowed
     */
    protected boolean maySubscribe(UUID userId, String destination) {
        return StompDestinations.userTopic(userId).equals(destination);
    }

    /**
     * Prefix of the destinations clients may send to; keep it equal to the one the socket config registers.
     *
     * @return the application destination prefix
     */
    protected String applicationDestinationPrefix() {
        return StompDestinations.APPLICATION_PREFIX;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            // Heartbeats and other frames without a command carry nothing to authorize.
            return message;
        }

        StompCommand command = accessor.getCommand();
        switch (command) {
            case CONNECT -> accessor.setUser(authenticate(accessor));
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case SEND -> authorizeSend(accessor);
            default -> {
                // UNSUBSCRIBE, DISCONNECT, ACK and NACK need no checks of their own.
            }
        }
        return message;
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        // Browsers cannot put headers on the WebSocket handshake, so the token travels in CONNECT.
        String header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new MessageDeliveryException("CONNECT requires an Authorization: Bearer <token> header");
        }

        try {
            Jwt jwt = jwtDecoder.decode(header.substring(BEARER_PREFIX.length()).trim());
            AbstractAuthenticationToken authentication = jwtAuthenticationConverter.convert(jwt);
            UUID userId = SecurityUtils.extractUserId(authentication);
            log.debug("STOMP session authenticated [userId={}, sessionId={}]", userId, accessor.getSessionId());
            return authentication;
        } catch (JwtException | AccessDeniedException e) {
            log.warn("STOMP CONNECT refused [sessionId={}, reason={}]", accessor.getSessionId(), e.getMessage());
            throw new MessageDeliveryException("Invalid token");
        }
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        UUID userId = requireLiveUser(accessor);
        String destination = accessor.getDestination();
        if (!maySubscribe(userId, destination)) {
            log.warn("STOMP SUBSCRIBE refused [userId={}, destination={}]", userId, destination);
            throw new MessageDeliveryException("You cannot subscribe to " + destination);
        }
    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        UUID userId = requireLiveUser(accessor);
        String destination = accessor.getDestination();
        String prefix = applicationDestinationPrefix();
        // Sending straight to a /topic/ would hand any message to the broker, past every check.
        if (destination == null || !destination.startsWith(prefix + "/")) {
            log.warn("STOMP SEND refused [userId={}, destination={}]", userId, destination);
            throw new MessageDeliveryException("You can only send to " + prefix + "/**");
        }
    }

    private UUID requireLiveUser(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new MessageDeliveryException("Not authenticated");
        }
        // The socket outlives the token it opened with; once that expires the client must reconnect.
        if (jwt.getExpiresAt() != null && jwt.getExpiresAt().isBefore(Instant.now())) {
            throw new MessageDeliveryException("Token expired, reconnect with a fresh token");
        }
        return SecurityUtils.extractUserId(authentication);
    }
}
