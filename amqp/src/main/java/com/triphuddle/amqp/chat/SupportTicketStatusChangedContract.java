package com.triphuddle.amqp.chat;

/**
 * Queue and dead-letter queue names for {@link SupportTicketStatusChangedEvent}.
 */
public class SupportTicketStatusChangedContract {
    public static final String SUPPORT_TICKET_STATUS_CHANGED_QUEUE_NAME = "chat.support-ticket.status-changed.queue";
    public static final String SUPPORT_TICKET_STATUS_CHANGED_DLQ_NAME = "chat.support-ticket.status-changed.dlq";
}
