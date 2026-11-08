package com.triphuddle.notification.messaging;

import com.triphuddle.amqp.notification.NotificationEmailContract;
import com.triphuddle.amqp.notification.SendEmailRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmailDlqListener {

    @RabbitListener(
            queues = NotificationEmailContract.EMAIL_DLQ_NAME,
            containerFactory = "notificationListenerContainerFactory"
    )
    public void onDeadLetter(SendEmailRequest request) {
        log.error("Email permanently failed after all retries — manual investigation required. " +
                "[type={}, to={}]", request.type(), request.recipientEmail());
    }
}
