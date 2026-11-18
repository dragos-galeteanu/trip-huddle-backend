package com.triphuddle.notification.config;

import com.triphuddle.amqp.notification.NotificationEmailContract;
import com.triphuddle.amqp.notification.NotificationPushContract;
import lombok.AllArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.util.Map;

/**
 * RabbitMQ listener setup for emails and pushes: retries with backoff, then each one's dead-letter queue.
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
    // Three tries for failures that may pass, such as an SMTP or FCM outage. A listener throws
    // AmqpRejectAndDontRequeueException for one that never will, which goes to its dead-letter queue at once;
    // the default policy would retry that too.
    private static SimpleRetryPolicy retryPolicy() {
        return new SimpleRetryPolicy(3, Map.of(AmqpRejectAndDontRequeueException.class, false), true, true);
    }

    @Bean
    public RetryOperationsInterceptor emailRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy())
                .backOffOptions(2000, 2.0, 8000)
                .recoverer(new RepublishMessageRecoverer(amqpTemplate, "", NotificationEmailContract.EMAIL_DLQ_NAME))
                .build();
    }

    /**
     * Listener container factory used by the push listeners.
     *
     * @return the listener container factory
     */
    @Bean
    public SimpleRabbitListenerContainerFactory pushListenerContainerFactory() {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jacksonConverter);
        factory.setAdviceChain(pushRetryInterceptor());
        return factory;
    }

    /**
     * Retries a failed push three times with exponential backoff, then republishes it to the push DLQ.
     *
     * @return the retry interceptor
     */
    @Bean
    public RetryOperationsInterceptor pushRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy())
                .backOffOptions(2000, 2.0, 8000)
                .recoverer(new RepublishMessageRecoverer(amqpTemplate, "", NotificationPushContract.PUSH_DLQ_NAME))
                .build();
    }
}
