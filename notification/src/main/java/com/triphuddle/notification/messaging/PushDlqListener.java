package com.triphuddle.notification.messaging;

import com.triphuddle.amqp.notification.NotificationPushContract;
import com.triphuddle.amqp.notification.SendPushRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the push dead-letter queue and logs every push that could not be sent.
 */
@Slf4j
@Component
public class PushDlqListener {

    /**
     * Log a push that failed after all retries, for manual investigation.
     *
     * @param request the push that could not be sent
     */
    @RabbitListener(
            queues = NotificationPushContract.PUSH_DLQ_NAME,
            containerFactory = "pushListenerContainerFactory"
    )
    public void onDeadLetter(SendPushRequest request) {
        log.error("Push permanently failed after all retries [recipients={}, title={}]",
                request.recipientUserIds().size(), request.title());
    }
}
