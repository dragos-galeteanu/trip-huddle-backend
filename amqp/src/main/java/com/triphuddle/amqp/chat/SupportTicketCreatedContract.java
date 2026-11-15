package com.triphuddle.amqp.chat;

/**
 * Queue and dead-letter queue names for {@link SupportTicketCreatedEvent}.
 */
public class SupportTicketCreatedContract {
    public static final String SUPPORT_TICKET_CREATED_QUEUE_NAME = "chat.support-ticket.created.queue";
    public static final String SUPPORT_TICKET_CREATED_DLQ_NAME = "chat.support-ticket.created.dlq";
}
