package com.triphuddle.amqp.destination;

import java.util.List;
import java.util.Map;

/**
 * Every destination exchange and the queues bound to it, one per consuming service.
 * {@code RabbitMQConfig} declares each as a durable fanout exchange with these queues bound to it.
 */
public class DestinationExchanges {
    public static final Map<String, List<String>> BINDINGS = Map.of(
            ItineraryFinishedContract.ITINERARY_FINISHED_EXCHANGE_NAME, List.of(
                    ItineraryFinishedContract.ITINERARY_FINISHED_CHAT_QUEUE_NAME,
                    ItineraryFinishedContract.ITINERARY_FINISHED_USER_QUEUE_NAME)
    );
}
