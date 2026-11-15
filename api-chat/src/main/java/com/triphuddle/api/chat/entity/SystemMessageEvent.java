package com.triphuddle.api.chat.entity;

import java.util.UUID;

/**
 * What a system message records, so that clients can render it in their own language.
 *
 * @param type the kind of change
 * @param targetUserId the user the change concerns, such as the staff member who joined, or null
 * @param fromValue the value before the change, or null
 * @param toValue the value after the change, or null
 */
public record SystemMessageEvent(SupportTicketEventType type, UUID targetUserId, String fromValue, String toValue) {
}
