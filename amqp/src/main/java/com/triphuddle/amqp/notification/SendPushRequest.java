package com.triphuddle.amqp.notification;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Asks the notification service to send one push notification to every device of the recipients.
 *
 * @param recipientUserIds the IDs of the users to notify
 * @param title the title of the notification
 * @param body the text of the notification
 * @param data key-value pairs the client app receives with the notification, or null for none
 */
public record SendPushRequest(
        List<UUID> recipientUserIds,
        String title,
        String body,
        Map<String, String> data
) {
}
