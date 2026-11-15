package com.triphuddle.api.chat.config;

import com.triphuddle.api.chat.security.ChatAccess;
import com.triphuddle.common.config.StompDestinations;
import com.triphuddle.common.security.BaseStompAuthChannelInterceptor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.UUID;

/**
 * Adds the support queue topic, which Support and Admin sessions may subscribe to, to the per-user topics.
 */
public class ChatStompAuthChannelInterceptor extends BaseStompAuthChannelInterceptor {

    // Dots, not slashes, after /topic/: RabbitMQ's STOMP plugin turns the rest into a topic routing key.
    public static final String SUPPORT_QUEUE_TOPIC = StompDestinations.BROKER_PREFIX + "/support.queue";

    private final ChatAccess chatAccess;

    public ChatStompAuthChannelInterceptor(
            JwtDecoder jwtDecoder,
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter,
            ChatAccess chatAccess) {
        super(jwtDecoder, jwtAuthenticationConverter);
        this.chatAccess = chatAccess;
    }

    @Override
    protected boolean maySubscribe(Authentication authentication, UUID userId, String destination) {
        if (SUPPORT_QUEUE_TOPIC.equals(destination)) {
            return chatAccess.isSupportStaff(authentication);
        }
        return super.maySubscribe(authentication, userId, destination);
    }
}
