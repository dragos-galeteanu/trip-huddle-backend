package com.triphuddle.amqp;

import com.triphuddle.amqp.platform.CountryCreatedContract;
import com.triphuddle.amqp.platform.CountryCreatedEvent;
import com.triphuddle.amqp.platform.CountryDeletedContract;
import com.triphuddle.amqp.platform.CountryDeletedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The broker setup every service shares, against a real RabbitMQ: queues declared at startup, JSON in and out,
 * and NFR-4's three attempts before a failing message goes to the dead-letter queue.
 */
@Testcontainers
@SpringBootTest(classes = {RabbitMQConfig.class, RabbitMQMessageProducer.class, RabbitMQIT.Listeners.class})
class RabbitMQIT {

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @DynamicPropertySource
    static void rabbit(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Component
    static class Listeners {

        final List<CountryCreatedEvent> created = new CopyOnWriteArrayList<>();
        final AtomicInteger deletedAttempts = new AtomicInteger();

        @RabbitListener(queues = CountryCreatedContract.COUNTRY_CREATED_QUEUE_NAME,
                containerFactory = "simpleRabbitListenerContainerFactory")
        void onCreated(CountryCreatedEvent event) {
            created.add(event);
        }

        @RabbitListener(queues = CountryDeletedContract.COUNTRY_DELETED_QUEUE_NAME,
                containerFactory = "simpleRabbitListenerContainerFactory")
        void onDeleted(CountryDeletedEvent event) {
            deletedAttempts.incrementAndGet();
            throw new IllegalStateException("this consumer is broken");
        }
    }

    @Autowired
    RabbitMQMessageProducer producer;

    // By name: Spring Boot adds its own rabbitTemplate next to the module's JSON amqpTemplate.
    @Autowired
    @Qualifier("amqpTemplate")
    AmqpTemplate template;

    @Autowired
    Listeners listeners;

    @Test
    void deliversAPublishedEventToItsListenerAsItsOwnType() {
        CountryCreatedEvent event = new CountryCreatedEvent(UUID.randomUUID(), "QB");

        producer.publish(event, "", CountryCreatedContract.COUNTRY_CREATED_QUEUE_NAME);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(listeners.created).contains(event));
    }

    @Test
    void triesAFailingMessageThreeTimesThenDeadLettersIt() {
        CountryDeletedEvent event = new CountryDeletedEvent(UUID.randomUUID(), "QC");

        producer.publish(event, "", CountryDeletedContract.COUNTRY_DELETED_QUEUE_NAME);

        Object deadLettered = template.receiveAndConvert(RabbitMQConfig.DEFAULT_DLQ_NAME, 20_000);
        assertThat(deadLettered).isEqualTo(event);
        assertThat(listeners.deletedAttempts).hasValue(3);
    }
}
