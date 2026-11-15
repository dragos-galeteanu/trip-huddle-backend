package com.triphuddle.api.chat.entity;

/**
 * {@code PRIVATE} is between two users, {@code GROUP} belongs to a group of api-group, and {@code SUPPORT}
 * is a support ticket between one customer and staff.
 */
public enum ConversationType {
    PRIVATE,
    GROUP,
    SUPPORT
}
