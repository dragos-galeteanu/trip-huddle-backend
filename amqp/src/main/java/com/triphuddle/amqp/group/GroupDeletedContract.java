package com.triphuddle.amqp.group;

/**
 * Queue and dead-letter queue names for {@link GroupDeletedEvent}.
 */
public class GroupDeletedContract {
    public static final String GROUP_DELETED_QUEUE_NAME = "group.deleted.queue";
    public static final String GROUP_DELETED_DLQ_NAME = "group.deleted.dlq";
}
