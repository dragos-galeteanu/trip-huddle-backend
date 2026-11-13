package com.triphuddle.amqp;

import com.triphuddle.amqp.group.GroupQueues;
import com.triphuddle.amqp.notification.NotificationQueues;
import com.triphuddle.amqp.platform.PlatformQueues;
import com.triphuddle.amqp.user.UserQueues;
import lombok.AllArgsConstructor;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Shared RabbitMQ setup: JSON messages, retrying listeners and every queue the contracts define.
 */
@Configuration
@Import(RabbitAutoConfiguration.class)
@AllArgsConstructor
@ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMQConfig {

    public static final String DEFAULT_DLQ_NAME = "default.dlq";

    private static final List<String> QUEUE_NAMES = Stream.of(
                    NotificationQueues.QUEUE_NAMES,
                    UserQueues.QUEUE_NAMES,
                    PlatformQueues.QUEUE_NAMES,
                    GroupQueues.QUEUE_NAMES
            )
            .flatMap(List::stream)
            .toList();

    private final ConnectionFactory connectionFactory;

    /**
     * Template for publishing messages as JSON.
     *
     * @return the AMQP template
     */
    @Bean
    public AmqpTemplate amqpTemplate() {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jacksonConverter());
        rabbitTemplate.setObservationEnabled(true);
        return rabbitTemplate;
    }

    /**
     * Default listener container factory: JSON messages and the default retry policy.
     *
     * @return the listener container factory
     */
    @Bean
    public SimpleRabbitListenerContainerFactory simpleRabbitListenerContainerFactory() {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jacksonConverter());
        factory.setAdviceChain(defaultRetryInterceptor());
        factory.setObservationEnabled(true);
        return factory;
    }

    /**
     * Retries a failed message three times with exponential backoff, then republishes it to the default DLQ.
     *
     * @return the retry interceptor
     */
    @Bean
    public RetryOperationsInterceptor defaultRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(2000, 2.0, 8000)
                .recoverer(new RepublishMessageRecoverer(amqpTemplate(), "", DEFAULT_DLQ_NAME))
                .build();
    }

    /**
     * Converts messages to and from JSON.
     *
     * @return the message converter
     */
    @Bean
    public MessageConverter jacksonConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Declares every contract queue and the default DLQ as durable queues on the broker.
     *
     * @return the queues to declare
     */
    @Bean
    public Declarables allQueues() {
        List<Declarable> declarableList = new ArrayList<>(
                QUEUE_NAMES.stream()
                        .map(name -> (Declarable) new Queue(name, true))
                        .toList()
        );
        declarableList.add(new Queue(DEFAULT_DLQ_NAME, true));
        return new Declarables(declarableList);
    }

}
