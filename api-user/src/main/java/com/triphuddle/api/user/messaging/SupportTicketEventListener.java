package com.triphuddle.api.user.messaging;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.chat.SupportTicketCreatedContract;
import com.triphuddle.amqp.chat.SupportTicketCreatedEvent;
import com.triphuddle.amqp.chat.SupportTicketRepliedContract;
import com.triphuddle.amqp.chat.SupportTicketRepliedEvent;
import com.triphuddle.amqp.chat.SupportTicketStatusChangedContract;
import com.triphuddle.amqp.chat.SupportTicketStatusChangedEvent;
import com.triphuddle.amqp.notification.NotificationEmailContract;
import com.triphuddle.amqp.notification.NotificationEmailType;
import com.triphuddle.amqp.notification.SendEmailRequest;
import com.triphuddle.api.user.entity.User;
import com.triphuddle.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Emails the customer of a support ticket when it is opened, answered, resolved or closed by someone else.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupportTicketEventListener {

    private final UserRepository userRepository;
    private final RabbitMQMessageProducer messageProducer;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Confirm a ticket to its customer, or tell them that support contacted them.
     *
     * @param event the opened ticket
     */
    @RabbitListener(queues = SupportTicketCreatedContract.SUPPORT_TICKET_CREATED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onSupportTicketCreated(SupportTicketCreatedEvent event) {
        log.info("Received support ticket created [ticketId={}]", event.ticketId());
        Map<String, Object> data = new HashMap<>();
        data.put("isOutbound", !event.requesterId().equals(event.openedByUserId()));
        sendToCustomer(NotificationEmailType.SUPPORT_TICKET_CREATED, event.requesterId(), event.ticketId(),
                event.reference(), event.subject(), data);
    }

    /**
     * Tell the customer that support answered their ticket.
     *
     * @param event the reply
     */
    @RabbitListener(queues = SupportTicketRepliedContract.SUPPORT_TICKET_REPLIED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onSupportTicketReplied(SupportTicketRepliedEvent event) {
        log.info("Received support ticket replied [ticketId={}]", event.ticketId());
        sendToCustomer(NotificationEmailType.SUPPORT_TICKET_REPLIED, event.requesterId(), event.ticketId(),
                event.reference(), event.subject(), new HashMap<>());
    }

    /**
     * Tell the customer their ticket was resolved, or closed by someone else.
     *
     * @param event the status change
     */
    @RabbitListener(queues = SupportTicketStatusChangedContract.SUPPORT_TICKET_STATUS_CHANGED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onSupportTicketStatusChanged(SupportTicketStatusChangedEvent event) {
        log.info("Received support ticket status changed [ticketId={}, toStatus={}]", event.ticketId(),
                event.toStatus());
        // A customer who closed their own ticket needs no email about it.
        boolean isClosedByOther = !event.requesterId().equals(event.actorId());
        NotificationEmailType type = switch (event.toStatus()) {
            case "RESOLVED" -> NotificationEmailType.SUPPORT_TICKET_RESOLVED;
            case "CLOSED" -> isClosedByOther ? NotificationEmailType.SUPPORT_TICKET_CLOSED : null;
            default -> null;
        };
        if (type != null) {
            sendToCustomer(type, event.requesterId(), event.ticketId(), event.reference(), event.subject(),
                    new HashMap<>());
        }
    }

    private void sendToCustomer(NotificationEmailType type, UUID requesterId, UUID ticketId, String reference,
                                String subject, Map<String, Object> data) {
        Optional<User> user = userRepository.findByIdAndDeletedAtIsNullAndDisabledAtIsNull(requesterId)
                .filter(User::isEmailVerified);
        if (user.isEmpty()) {
            log.info("No verified user to email about support ticket [ticketId={}, type={}]", ticketId, type);
            return;
        }

        data.put("firstName", user.get().getFirstName());
        data.put("reference", reference);
        data.put("subject", subject);
        data.put("ticketUrl", "%s/support/tickets/%s".formatted(frontendUrl, ticketId));
        messageProducer.publish(new SendEmailRequest(type, user.get().getEmail(), data), "",
                NotificationEmailContract.EMAIL_QUEUE_NAME);
        log.info("Event published for support ticket email [ticketId={}, type={}, userId={}]",
                ticketId, type, requesterId);
    }
}
