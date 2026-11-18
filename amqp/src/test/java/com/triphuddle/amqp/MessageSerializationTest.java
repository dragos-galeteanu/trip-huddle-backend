package com.triphuddle.amqp;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Every message survives the trip through the converter the services use, field for field, so a publisher and its
 * consumers can never disagree about a message's shape.
 */
class MessageSerializationTest {

    private final Jackson2JsonMessageConverter converter = (Jackson2JsonMessageConverter)
            new RabbitMQConfig(null).jacksonConverter();

    @Test
    void findsTheMessages() {
        assertThat(Messages.all()).hasSizeGreaterThanOrEqualTo(60);
    }

    @TestFactory
    Stream<DynamicTest> everyMessageSurvivesTheRoundTrip() {
        return Messages.all().stream().map(type -> dynamicTest(type.getSimpleName(), () -> {
            Object sent = new SampleValues().of(type);

            Message message = converter.toMessage(sent, new MessageProperties());
            Object received = converter.fromMessage(message);

            assertThat(received).isInstanceOf(type);
            assertThat(received).usingRecursiveComparison().isEqualTo(sent);
            assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        }));
    }
}
