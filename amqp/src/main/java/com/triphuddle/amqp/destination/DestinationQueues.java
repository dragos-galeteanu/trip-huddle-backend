package com.triphuddle.amqp.destination;

import java.util.List;

/** Every queue the destination contracts define, declared on the broker by {@code RabbitMQConfig}. */
public class DestinationQueues {
    public static final List<String> QUEUE_NAMES = List.of(
            ItineraryFinishedContract.ITINERARY_FINISHED_CHAT_QUEUE_NAME,
            ItineraryFinishedContract.ITINERARY_FINISHED_CHAT_DLQ_NAME,
            ItineraryFinishedContract.ITINERARY_FINISHED_USER_QUEUE_NAME,
            ItineraryFinishedContract.ITINERARY_FINISHED_USER_DLQ_NAME
    );
}
