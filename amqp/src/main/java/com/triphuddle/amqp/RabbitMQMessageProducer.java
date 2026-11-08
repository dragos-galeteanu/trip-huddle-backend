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

    public void publish(Object payload, String exchange, String routingKey) {
        log.info("Publishing to {} using routingKey {}. Payload: {}", exchange, routingKey, payload);
        amqpTemplate.convertAndSend(exchange, routingKey, payload);
        log.info("Published to {} using routingKey {}. Payload: {}", exchange, routingKey, payload);
    }

}
