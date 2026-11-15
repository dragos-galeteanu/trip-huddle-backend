package com.triphuddle.amqp.destination;

/**
 * Names for {@link ItineraryFinishedEvent}. It is published once to a fanout exchange, and each consuming service
 * reads its own queue bound to it: {@code CHAT_} api-chat's, which tells sockets, and {@code USER_} api-user's,
 * which sends the email.
 */
public class ItineraryFinishedContract {
    public static final String ITINERARY_FINISHED_EXCHANGE_NAME = "destination.itinerary.finished.exchange";
    public static final String ITINERARY_FINISHED_CHAT_QUEUE_NAME = "destination.itinerary.finished.chat.queue";
    public static final String ITINERARY_FINISHED_CHAT_DLQ_NAME = "destination.itinerary.finished.chat.dlq";
    public static final String ITINERARY_FINISHED_USER_QUEUE_NAME = "destination.itinerary.finished.user.queue";
    public static final String ITINERARY_FINISHED_USER_DLQ_NAME = "destination.itinerary.finished.user.dlq";
}
