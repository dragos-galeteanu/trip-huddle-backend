package com.triphuddle.amqp;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.Mockito.mock;

class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig(mock(ConnectionFactory.class));

    @Test
    void declaresEveryQueueDurableOnceWithTheDeadLetterQueue() {
        List<Queue> queues = queues();

        assertThat(queues).allMatch(Queue::isDurable);
        assertThat(queues).extracting(Queue::getName).doesNotHaveDuplicates().contains(RabbitMQConfig.DEFAULT_DLQ_NAME);
    }

    @TestFactory
    Stream<DynamicTest> declaresEveryQueueAContractNames() {
        Set<String> declared = queues().stream().map(Queue::getName).collect(Collectors.toSet());
        return Messages.endingWith("Contract").stream().map(contract -> dynamicTest(contract.getSimpleName(), () ->
                assertThat(declared).as("queues of %s", contract.getSimpleName())
                        .containsAll(constants(contract, "_QUEUE_NAME", "_DLQ_NAME"))));
    }

    @Test
    void bindsEachFanoutExchangeOnlyToDeclaredQueues() {
        List<Declarable> declarables = config.allExchanges().getDeclarables().stream().toList();
        Set<String> declared = queues().stream().map(Queue::getName).collect(Collectors.toSet());
        List<FanoutExchange> exchanges = declarables.stream()
                .filter(FanoutExchange.class::isInstance).map(FanoutExchange.class::cast).toList();
        List<Binding> bindings = declarables.stream()
                .filter(Binding.class::isInstance).map(Binding.class::cast).toList();

        assertThat(exchanges).isNotEmpty().allMatch(e -> e.isDurable() && !e.isAutoDelete());
        assertThat(bindings).allSatisfy(binding -> {
            assertThat(declared).contains(binding.getDestination());
            assertThat(exchanges).extracting(FanoutExchange::getName).contains(binding.getExchange());
        });
        assertThat(exchanges).allSatisfy(exchange -> assertThat(bindings)
                .anyMatch(binding -> binding.getExchange().equals(exchange.getName())));
    }

    @Test
    void anExchangeBindingAQueueNoQueuesClassListsFailsStartup() {
        assertThatThrownBy(() -> RabbitMQConfig.exchangeDeclarables(
                Map.of("chat.message.created.exchange", List.of("listed.queue", "forgotten.queue")),
                List.of("listed.queue")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Exchange chat.message.created.exchange binds queue forgotten.queue, "
                        + "which no *Queues class lists");
    }

    @TestFactory
    Stream<DynamicTest> declaresEveryExchangeAContractNames() {
        Set<String> declared = config.allExchanges().getDeclarables().stream()
                .filter(FanoutExchange.class::isInstance).map(d -> ((FanoutExchange) d).getName())
                .collect(Collectors.toSet());
        return Messages.endingWith("Contract").stream()
                .filter(contract -> !constants(contract, "_EXCHANGE_NAME").isEmpty())
                .map(contract -> dynamicTest(contract.getSimpleName(), () ->
                        assertThat(declared).containsAll(constants(contract, "_EXCHANGE_NAME"))));
    }

    @Test
    void sendsAndReceivesJsonAndRetriesBeforeDeadLettering() {
        assertThat(config.jacksonConverter()).isInstanceOf(Jackson2JsonMessageConverter.class);
        assertThat(((RabbitTemplate) config.amqpTemplate()).getMessageConverter())
                .isInstanceOf(Jackson2JsonMessageConverter.class);
        assertThat(config.simpleRabbitListenerContainerFactory().getAdviceChain())
                .singleElement().isNotNull();
        assertThat(config.defaultRetryInterceptor()).isNotNull();
    }

    private List<Queue> queues() {
        return config.allQueues().getDeclarables().stream()
                .filter(Queue.class::isInstance).map(Queue.class::cast).toList();
    }

    private static List<String> constants(Class<?> type, String... suffixes) {
        return Stream.of(type.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()) && f.getType() == String.class)
                .filter(f -> Stream.of(suffixes).anyMatch(f.getName()::endsWith))
                .map(RabbitMQConfigTest::read)
                .toList();
    }

    private static String read(Field field) {
        try {
            field.setAccessible(true);
            return (String) field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
