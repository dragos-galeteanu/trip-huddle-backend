package com.triphuddle.api.chat.config;

/**
 * Settings under {@code app.chat.support}: the open-ticket limit and the auto-closing of resolved tickets.
 *
 * @param maxOngoingTicketsPerCustomer how many unresolved tickets a customer may have opened at once
 * @param autoCloseAfterDays how long a ticket stays RESOLVED before it closes on its own
 * @param autoCloseIntervalMs the pause between runs of the auto-close job
 */
public record SupportProperties(int maxOngoingTicketsPerCustomer, int autoCloseAfterDays, long autoCloseIntervalMs) {
}
