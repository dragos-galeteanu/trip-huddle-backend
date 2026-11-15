package com.triphuddle.api.chat.config;

import com.triphuddle.common.config.BaseWebSocketConfig;
import com.triphuddle.common.config.StompBrokerProperties;
import com.triphuddle.common.security.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;

import java.util.List;

/**
 * STOMP over WebSocket at {@code /ws/chats}, brokered by RabbitMQ unless the relay is turned off.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements BaseWebSocketConfig {

    public static final String ENDPOINT = "/ws/chats";

    private final ChatProperties chatProperties;
    private final JwtDecoder jwtDecoder;
    private final Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Override
    public String endpoint() {
        return ENDPOINT;
    }

    @Override
    public List<String> allowedOrigins() {
        return allowedOrigins;
    }

    @Override
    public StompBrokerProperties broker() {
        return chatProperties.broker();
    }

    @Override
    public List<ChannelInterceptor> inboundInterceptors() {
        return List.of(new StompAuthChannelInterceptor(jwtDecoder, jwtAuthenticationConverter));
    }
}
