package com.triphuddle.amqp.notification;

import java.util.List;
import java.util.Map;

/**
 * Asks the notification service to send one email.
 *
 * @param type the kind of email, which selects the template and the subject
 * @param recipientEmail the email address to send to
 * @param templateData the variables the template is rendered with
 * @param attachments the files to attach, or null for none
 */
public record SendEmailRequest(
        NotificationEmailType type,
        String recipientEmail,
        Map<String, Object> templateData,
        List<EmailAttachment> attachments
) {

    /**
     * Create a request for an email without attachments.
     *
     * @param type the kind of email
     * @param recipientEmail the email address to send to
     * @param templateData the variables the template is rendered with
     */
    public SendEmailRequest(NotificationEmailType type, String recipientEmail, Map<String, Object> templateData) {
        this(type, recipientEmail, templateData, null);
    }
}
