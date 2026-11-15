package com.triphuddle.api.user.messaging;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.destination.ItineraryFinishedContract;
import com.triphuddle.amqp.destination.ItineraryFinishedEvent;
import com.triphuddle.amqp.notification.NotificationEmailContract;
import com.triphuddle.amqp.notification.NotificationEmailType;
import com.triphuddle.amqp.notification.SendEmailRequest;
import com.triphuddle.api.user.entity.User;
import com.triphuddle.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Emails whoever asked for an itinerary once it is ready.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItineraryEventListener {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final UserRepository userRepository;
    private final RabbitMQMessageProducer messageProducer;

    /**
     * Email the requester a link to their itinerary; a failed one only shows in the app.
     *
     * @param event the finished itinerary
     */
    @RabbitListener(queues = ItineraryFinishedContract.ITINERARY_FINISHED_USER_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onFinished(ItineraryFinishedEvent event) {
        log.info("Received itinerary finished [itineraryId={}, status={}]", event.itineraryId(), event.status());
        if (!"READY".equals(event.status()) || event.requestedByUserId() == null) {
            return;
        }
        Optional<User> user = userRepository.findByIdAndDeletedAtIsNullAndDisabledAtIsNull(event.requestedByUserId())
                .filter(User::isEmailVerified);
        if (user.isEmpty()) {
            log.info("No verified user to email about itinerary [itineraryId={}]", event.itineraryId());
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("firstName", user.get().getFirstName());
        data.put("planTitle", event.planTitle());
        data.put("startDate", DATE_FORMAT.format(event.startDate()));
        data.put("dayCount", event.dayCount());
        data.put("planUrl", event.planUrl());
        SendEmailRequest email = new SendEmailRequest(NotificationEmailType.ITINERARY_READY, user.get().getEmail(), data);
        messageProducer.publish(email, "", NotificationEmailContract.EMAIL_QUEUE_NAME);
        log.info("Event published for itinerary email [itineraryId={}, userId={}]", event.itineraryId(),
                event.requestedByUserId());
    }
}
