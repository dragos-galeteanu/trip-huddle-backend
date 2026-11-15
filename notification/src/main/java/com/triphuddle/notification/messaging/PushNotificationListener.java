package com.triphuddle.notification.messaging;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.triphuddle.amqp.notification.NotificationPushContract;
import com.triphuddle.amqp.notification.SendPushRequest;
import com.triphuddle.notification.service.PushService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes push requests from the push queue and hands them to {@link PushService}.
 */
@Slf4j
@Component
@AllArgsConstructor
public class PushNotificationListener {

    private final PushService pushService;

    /**
     * Send the requested push; an FCM failure is retried, any other failure is dead-lettered at once.
     *
     * @param request the push to send
     * @throws FirebaseMessagingException if FCM fails in a way worth retrying
     */
    @RabbitListener(
            queues = NotificationPushContract.PUSH_QUEUE_NAME,
            containerFactory = "pushListenerContainerFactory"
    )
    public void onPushRequest(SendPushRequest request) throws FirebaseMessagingException {
        log.info("Received push request [recipients={}]", request.recipientUserIds().size());
        try {
            pushService.send(request);
        } catch (FirebaseMessagingException e) {
            log.error("Transient push failure, will retry [recipients={}]", request.recipientUserIds().size(), e);
            throw e;
        } catch (Exception e) {
            log.error("Permanent push failure, sending to DLQ [recipients={}]", request.recipientUserIds().size(), e);
            throw new AmqpRejectAndDontRequeueException("Permanent push failure", e);
        }
    }
}
