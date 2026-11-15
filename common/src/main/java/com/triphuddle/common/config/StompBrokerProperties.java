package com.triphuddle.common.config;

/**
 * The STOMP broker: RabbitMQ's STOMP plugin when relayed, otherwise an in-memory broker.
 *
 * @param relayEnabled whether to relay to RabbitMQ; required for more than one instance
 * @param relayHost the RabbitMQ host
 * @param relayPort the port of RabbitMQ's STOMP plugin
 * @param login the RabbitMQ user
 * @param passcode the RabbitMQ password
 * @param virtualHost the RabbitMQ virtual host
 */
public record StompBrokerProperties(boolean relayEnabled, String relayHost, int relayPort,
                                    String login, String passcode, String virtualHost) {
}
