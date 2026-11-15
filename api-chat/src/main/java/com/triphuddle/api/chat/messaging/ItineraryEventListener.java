package com.triphuddle.api.chat.messaging;

import com.triphuddle.amqp.destination.ItineraryFinishedContract;
import com.triphuddle.amqp.destination.ItineraryFinishedEvent;
import com.triphuddle.api.chat.dto.ChatEventType;
import com.triphuddle.api.chat.service.ChatEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Tells the travellers' open sockets when api-destination finishes writing an itinerary.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItineraryEventListener {

    private final ChatEventPublisher eventPublisher;

    /**
     * Send the finished itinerary to each traveller's user topic.
     *
     * @param event the itinerary, READY or FAILED
     */
    @RabbitListener(queues = ItineraryFinishedContract.ITINERARY_FINISHED_CHAT_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onFinished(ItineraryFinishedEvent event) {
        log.info("Received itinerary finished [itineraryId={}, status={}]", event.itineraryId(), event.status());
        // Not in a transaction, so the publisher sends at once.
        eventPublisher.publish(event.travellerUserIds(), ChatEventType.ITINERARY_FINISHED, null, event);
    }
}
