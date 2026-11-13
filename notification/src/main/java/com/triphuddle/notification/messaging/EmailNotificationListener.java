package com.triphuddle.notification.messaging;

import com.triphuddle.amqp.notification.NotificationEmailContract;
import com.triphuddle.amqp.notification.SendEmailRequest;
import com.triphuddle.notification.service.EmailService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;

/**
 * Consumes email requests from the email queue and hands them to {@link EmailService}.
 */
@Slf4j
@Component
@AllArgsConstructor
public class EmailNotificationListener {

    private final EmailService emailService;

    /**
     * Send the requested email; a mail failure is retried, any other failure is dead-lettered at once.
     *
     * @param request the email to send
     * @throws MailException if sending fails in a way worth retrying
     */
    @RabbitListener(
            queues = NotificationEmailContract.EMAIL_QUEUE_NAME,
            containerFactory = "notificationListenerContainerFactory"
    )
    public void onEmailRequest(SendEmailRequest request) throws MailException {
        log.info("Received email request [type={}, to={}]", request.type(), request.recipientEmail());
        try {
            emailService.send(request);
            log.info("Email sent successfully [type={}, to={}]", request.type(), request.recipientEmail());
        } catch (MailException e) {
            // Transient failure (SMTP connection, timeout) — rethrow to trigger retry
            log.error("Transient email failure, will retry [type={}, to={}]", request.type(), request.recipientEmail(), e);
            throw e;
        } catch (Exception e) {
            // Permanent failure (message construction, bad template) — dead-letter immediately
            log.error("Permanent email failure, sending to DLQ [type={}, to={}]", request.type(), request.recipientEmail(), e);
            throw new AmqpRejectAndDontRequeueException("Permanent email failure", e);
        }
    }
}
