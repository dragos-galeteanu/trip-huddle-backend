package com.triphuddle.amqp.chat;

/**
 * Queue and dead-letter queue names for {@link SupportTicketRepliedEvent}.
 */
public class SupportTicketRepliedContract {
    public static final String SUPPORT_TICKET_REPLIED_QUEUE_NAME = "chat.support-ticket.replied.queue";
    public static final String SUPPORT_TICKET_REPLIED_DLQ_NAME = "chat.support-ticket.replied.dlq";
}
