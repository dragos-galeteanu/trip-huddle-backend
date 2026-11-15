package com.triphuddle.api.chat.entity;

/**
 * The changes recorded in a support ticket's history, each also posted as a system message.
 */
public enum SupportTicketEventType {
    CREATED,
    STATUS_CHANGED,
    PRIORITY_CHANGED,
    CATEGORY_CHANGED,
    ASSIGNED,
    UNASSIGNED,
    STAFF_JOINED,
    STAFF_LEFT,
    RATED
}
