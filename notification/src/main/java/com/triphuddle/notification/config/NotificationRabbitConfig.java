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

@Configuration
@AllArgsConstructor
public class NotificationRabbitConfig {

    private final ConnectionFactory connectionFactory;
    private final MessageConverter jacksonConverter;
    private final AmqpTemplate amqpTemplate;

    @Bean
    public SimpleRabbitListenerContainerFactory notificationListenerContainerFactory() {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jacksonConverter);
        factory.setAdviceChain(emailRetryInterceptor());
        return factory;
    }

    @Bean
    public RetryOperationsInterceptor emailRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(2000, 2.0, 8000)
                .recoverer(new RepublishMessageRecoverer(amqpTemplate, "", NotificationEmailContract.EMAIL_DLQ_NAME))
                .build();
    }
}
