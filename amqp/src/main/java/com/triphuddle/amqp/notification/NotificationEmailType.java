package com.triphuddle.amqp.notification;

/**
 * The kinds of email the notification service can send; each has its own template and subject.
 */
public enum NotificationEmailType {
    EMAIL_VERIFICATION,
    PASSWORD_RESET,
    WELCOME,
    PROFILE_PICTURE_UPDATED,
    GROUP_INVITATION
}
