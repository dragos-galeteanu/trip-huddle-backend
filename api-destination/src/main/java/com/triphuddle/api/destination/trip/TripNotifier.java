package com.triphuddle.api.destination.trip;

import com.triphuddle.api.destination.service.AfterCommit;
import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.destination.ItineraryFinishedContract;
import com.triphuddle.amqp.destination.ItineraryFinishedEvent;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.amqp.notification.NotificationPushContract;
import com.triphuddle.amqp.notification.SendPushRequest;
import com.triphuddle.api.destination.config.TripProperties;
import com.triphuddle.api.destination.entity.Itinerary;
import com.triphuddle.api.destination.entity.ItineraryStatus;
import com.triphuddle.api.destination.entity.TripPlan;
import com.triphuddle.api.destination.repository.GroupMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tells travellers an itinerary is ready or failed: the plan's travellers on their socket (through api-chat), the
 * one who asked by push and, when ready, by email (through api-user).
 */
@Component
@RequiredArgsConstructor
public class TripNotifier {

    private final RabbitMQMessageProducer messageProducer;
    private final GroupMemberRepository groupMemberRepository;
    private final TripProperties properties;

    /**
     * Tell the travellers an itinerary finished, once the transaction commits.
     *
     * @param itinerary the itinerary, READY or FAILED
     */
    public void itineraryFinished(Itinerary itinerary) {
        TripPlan plan = itinerary.getPlan();
        List<UUID> travellers = plan.getOwnerType() == LicenseTarget.USER ? List.of(plan.getOwnerId())
                : groupMemberRepository.findUserIdsByGroupId(plan.getOwnerId());
        boolean isReady = itinerary.getStatus() == ItineraryStatus.READY;
        String planUrl = properties.planUrl().replace("{planId}", plan.getId().toString());
        ItineraryFinishedEvent event = new ItineraryFinishedEvent(itinerary.getId(), plan.getId(), plan.getTitle(),
                itinerary.getStatus().name(), travellers, itinerary.getRequestedByUserId(), plan.getStartDate(),
                plan.dayCount(), planUrl);
        SendPushRequest push = new SendPushRequest(List.of(itinerary.getRequestedByUserId()),
                isReady ? "Your itinerary is ready" : "Your itinerary couldn't be written",
                isReady ? plan.getTitle() + ": " + plan.dayCount() + " days planned" : plan.getTitle() + ": try again",
                Map.of("type", "ITINERARY_FINISHED", "planId", plan.getId().toString(),
                        "itineraryId", itinerary.getId().toString(), "status", itinerary.getStatus().name()));
        AfterCommit.run(() -> {
            messageProducer.publish(event, ItineraryFinishedContract.ITINERARY_FINISHED_EXCHANGE_NAME, "");
            messageProducer.publish(push, "", NotificationPushContract.PUSH_QUEUE_NAME);
        });
    }

}
