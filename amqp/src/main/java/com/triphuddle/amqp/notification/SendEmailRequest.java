package com.triphuddle.amqp.notification;

import java.util.List;
import java.util.Map;

public record SendEmailRequest(
        NotificationEmailType type,
        String recipientEmail,
        Map<String, Object> templateData,
        List<EmailAttachment> attachments
) {

    public SendEmailRequest(NotificationEmailType type, String recipientEmail, Map<String, Object> templateData) {
        this(type, recipientEmail, templateData, null);
    }
}
