package com.triphuddle.amqp;

import com.triphuddle.amqp.notification.NotificationEmailContract;
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

@Configuration
@Import(RabbitAutoConfiguration.class)
@AllArgsConstructor
@ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMQConfig {

    public static final String DEFAULT_DLQ_NAME = "default.dlq";

    private static final List<String> QUEUE_NAMES = List.of(
            NotificationEmailContract.EMAIL_QUEUE_NAME,
            NotificationEmailContract.EMAIL_DLQ_NAME
    );

    private final ConnectionFactory connectionFactory;

    @Bean
    public AmqpTemplate amqpTemplate() {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jacksonConverter());
        rabbitTemplate.setObservationEnabled(true);
        return rabbitTemplate;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory simpleRabbitListenerContainerFactory() {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jacksonConverter());
        factory.setAdviceChain(defaultRetryInterceptor());
        factory.setObservationEnabled(true);
        return factory;
    }

    @Bean
    public RetryOperationsInterceptor defaultRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(2000, 2.0, 8000)
                .recoverer(new RepublishMessageRecoverer(amqpTemplate(), "", DEFAULT_DLQ_NAME))
                .build();
    }

    @Bean
    public MessageConverter jacksonConverter() {
        return new Jackson2JsonMessageConverter();
    }

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
