package com.triphuddle.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * Base STOMP over WebSocket configuration: one endpoint, brokered by RabbitMQ unless the relay is turned off.
 * The implementing class carries {@code @Configuration} and {@code @EnableWebSocketMessageBroker}.
 */
public interface BaseWebSocketConfig extends WebSocketMessageBrokerConfigurer {

    Logger log = LoggerFactory.getLogger(BaseWebSocketConfig.class);

    /**
     * Path of the WebSocket handshake.
     * @return the endpoint path
     */
    String endpoint();

    /**
     * Origin patterns a browser may open the socket from.
     * @return the allowed origin patterns
     */
    List<String> allowedOrigins();

    /**
     * How socket traffic is brokered.
     * @return the broker settings
     */
    StompBrokerProperties broker();

    /**
     * Prefix of the destinations routed to {@code @MessageMapping} methods.
     * @return the application destination prefix
     */
    default String applicationDestinationPrefix() {
        return StompDestinations.APPLICATION_PREFIX;
    }

    /**
     * Prefix of the destinations handed to the broker.
     * @return the broker destination prefix
     */
    default String brokerDestinationPrefix() {
        return StompDestinations.BROKER_PREFIX;
    }

    /**
     * Interceptors run on every frame a client sends, typically to authenticate and authorize it.
     * @return the inbound channel interceptors
     */
    default List<ChannelInterceptor> inboundInterceptors() {
        return List.of();
    }

    @Override
    default void registerStompEndpoints(StompEndpointRegistry registry) {
        // A handshake without an Origin header, as mobile apps send it, is not subject to this check.
        registry.addEndpoint(endpoint()).setAllowedOriginPatterns(allowedOrigins().toArray(String[]::new));
    }

    @Override
    default void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes(applicationDestinationPrefix());

        StompBrokerProperties broker = broker();
        if (broker.relayEnabled()) {
            // The relay opens its TCP connections through Reactor Netty, which the service must depend on.
            log.info("Relaying STOMP to RabbitMQ [host={}, port={}]", broker.relayHost(), broker.relayPort());
            registry.enableStompBrokerRelay(brokerDestinationPrefix())
                    .setRelayHost(broker.relayHost())
                    .setRelayPort(broker.relayPort())
                    .setClientLogin(broker.login())
                    .setClientPasscode(broker.passcode())
                    .setSystemLogin(broker.login())
                    .setSystemPasscode(broker.passcode())
                    .setVirtualHost(broker.virtualHost());
        } else {
            log.warn("Using the in-memory STOMP broker: events only reach clients of this instance");
            registry.enableSimpleBroker(brokerDestinationPrefix());
        }
    }

    @Override
    default void configureClientInboundChannel(ChannelRegistration registration) {
        List<ChannelInterceptor> interceptors = inboundInterceptors();
        if (!interceptors.isEmpty()) {
            registration.interceptors(interceptors.toArray(ChannelInterceptor[]::new));
        }
    }
}
