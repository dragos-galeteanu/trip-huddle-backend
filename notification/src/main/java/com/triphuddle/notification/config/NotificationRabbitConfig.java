package com.triphuddle.notification.config;

import com.triphuddle.amqp.notification.NotificationEmailContract;
import lombok.AllArgsConstructor;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

/**
 * RabbitMQ listener setup for emails: retries with backoff, then the email dead-letter queue.
 */
@Configuration
@AllArgsConstructor
public class NotificationRabbitConfig {

    private final ConnectionFactory connectionFactory;
    private final MessageConverter jacksonConverter;
    private final AmqpTemplate amqpTemplate;

    /**
     * Listener container factory used by the email listeners.
     *
     * @return the listener container factory
     */
    @Bean
    public SimpleRabbitListenerContainerFactory notificationListenerContainerFactory() {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jacksonConverter);
        factory.setAdviceChain(emailRetryInterceptor());
        return factory;
    }

    /**
     * Retries a failed email three times with exponential backoff, then republishes it to the email DLQ.
     *
     * @return the retry interceptor
     */
    @Bean
    public RetryOperationsInterceptor emailRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(2000, 2.0, 8000)
                .recoverer(new RepublishMessageRecoverer(amqpTemplate, "", NotificationEmailContract.EMAIL_DLQ_NAME))
                .build();
    }
}
