package com.triphuddle.amqp;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;

/**
 * Registered as a bean by {@link RabbitMQConfig} rather than annotated {@code @Component}: this
 * package sits outside every service's component-scan base package, so a stereotype annotation
 * here would never be found.
 */
@Slf4j
@AllArgsConstructor
public class RabbitMQMessageProducer {

    private final AmqpTemplate amqpTemplate;

    /**
     * Publish a payload as JSON; an empty exchange with a queue name as routing key goes straight to that queue.
     *
     * @param payload the message to publish
     * @param exchange the exchange to publish to, or an empty string for the default exchange
     * @param routingKey the routing key, or the queue name on the default exchange
     */
    public void publish(Object payload, String exchange, String routingKey) {
        log.info("Publishing to {} using routingKey {}. Payload: {}", exchange, routingKey, payload);
        amqpTemplate.convertAndSend(exchange, routingKey, payload);
        log.info("Published to {} using routingKey {}. Payload: {}", exchange, routingKey, payload);
    }

}
