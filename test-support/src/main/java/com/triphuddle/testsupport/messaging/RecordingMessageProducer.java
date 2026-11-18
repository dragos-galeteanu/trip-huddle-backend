package com.triphuddle.testsupport.messaging;

import com.triphuddle.amqp.RabbitMQMessageProducer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Records what would have been published instead of sending it.
 */
public class RecordingMessageProducer extends RabbitMQMessageProducer {

    /**
     * One publish call.
     *
     * @param payload the event
     * @param exchange the exchange, empty for the default one
     * @param routingKey the queue, or the routing key on an exchange
     */
    public record Published(Object payload, String exchange, String routingKey) {
    }

    private final List<Published> published = new CopyOnWriteArrayList<>();

    public RecordingMessageProducer() {
        super(null);
    }

    @Override
    public void publish(Object payload, String exchange, String routingKey) {
        published.add(new Published(payload, exchange, routingKey));
    }

    public List<Published> published() {
        return List.copyOf(published);
    }

    public void reset() {
        published.clear();
    }

    /**
     * Assert this event, equal by value, was published to this queue on the default exchange.
     */
    public void assertPublished(Object payload, String queue) {
        assertThat(published).contains(new Published(payload, "", queue));
    }

    /**
     * Assert events of this type were published this many times, whatever their content.
     */
    public void assertPublishedCount(Class<?> type, int count) {
        assertThat(published).filteredOn(p -> type.isInstance(p.payload())).hasSize(count);
    }

    public void assertNothingPublished() {
        assertThat(published).isEmpty();
    }
}
