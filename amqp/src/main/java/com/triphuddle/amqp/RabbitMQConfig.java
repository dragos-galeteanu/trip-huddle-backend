package com.triphuddle.amqp;

import com.triphuddle.amqp.ai.AiQueues;
import com.triphuddle.amqp.chat.ChatQueues;
import com.triphuddle.amqp.destination.DestinationExchanges;
import com.triphuddle.amqp.destination.DestinationQueues;
import com.triphuddle.amqp.group.GroupExchanges;
import com.triphuddle.amqp.group.GroupQueues;
import com.triphuddle.amqp.license.LicenseExchanges;
import com.triphuddle.amqp.license.LicenseQueues;
import com.triphuddle.amqp.notification.NotificationQueues;
import com.triphuddle.amqp.payment.PaymentQueues;
import com.triphuddle.amqp.platform.PlatformQueues;
import com.triphuddle.amqp.user.UserExchanges;
import com.triphuddle.amqp.user.UserQueues;
import lombok.AllArgsConstructor;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.FanoutExchange;
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
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Shared RabbitMQ setup: JSON messages, retrying listeners, every queue the contracts define, and the fanout
 * exchanges of events with several consumers.
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
                    GroupQueues.QUEUE_NAMES,
                    ChatQueues.QUEUE_NAMES,
                    LicenseQueues.QUEUE_NAMES,
                    PaymentQueues.QUEUE_NAMES,
                    AiQueues.QUEUE_NAMES,
                    DestinationQueues.QUEUE_NAMES
            )
            .flatMap(List::stream)
            .toList();

    // Fanout exchanges of the events several services consume, each with one bound queue per service.
    private static final Map<String, List<String>> EXCHANGE_BINDINGS = Stream.of(
                    GroupExchanges.BINDINGS,
                    UserExchanges.BINDINGS,
                    LicenseExchanges.BINDINGS,
                    DestinationExchanges.BINDINGS
            )
            .flatMap(bindings -> bindings.entrySet().stream())
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

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

    /**
     * Declares the fanout exchanges as durable, and binds each to its consuming services' queues.
     *
     * @return the exchanges and bindings to declare
     */
    @Bean
    public Declarables allExchanges() {
        return new Declarables(exchangeDeclarables());
    }

    static List<Declarable> exchangeDeclarables() {
        List<Declarable> declarables = new ArrayList<>();
        EXCHANGE_BINDINGS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    FanoutExchange exchange = new FanoutExchange(entry.getKey(), true, false);
                    declarables.add(exchange);
                    for (String queueName : entry.getValue()) {
                        if (!QUEUE_NAMES.contains(queueName)) {
                            throw new IllegalStateException("Exchange %s binds queue %s, which no *Queues class lists"
                                    .formatted(entry.getKey(), queueName));
                        }
                        declarables.add(BindingBuilder.bind(new Queue(queueName, true)).to(exchange));
                    }
                });
        return declarables;
    }

}
