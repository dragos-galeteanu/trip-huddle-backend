package com.triphuddle.api.chat.entity;

/**
 * What a support ticket is about; {@code SAFETY} tickets start at {@code HIGH} priority.
 */
public enum TicketCategory {
    ACCOUNT,
    GROUP,
    PAYMENT,
    SAFETY,
    BUG,
    OTHER
}
