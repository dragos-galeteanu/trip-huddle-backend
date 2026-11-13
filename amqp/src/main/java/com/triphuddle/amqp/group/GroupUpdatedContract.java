package com.triphuddle.amqp.group;

/**
 * Queue and dead-letter queue names for {@link GroupUpdatedEvent}.
 */
public class GroupUpdatedContract {
    public static final String GROUP_UPDATED_QUEUE_NAME = "group.updated.queue";
    public static final String GROUP_UPDATED_DLQ_NAME = "group.updated.dlq";
}
