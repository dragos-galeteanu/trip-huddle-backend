package com.triphuddle.amqp.group;

/**
 * Queue and dead-letter queue names for {@link GroupCreatedEvent}.
 */
public class GroupCreatedContract {
    public static final String GROUP_CREATED_QUEUE_NAME = "group.created.queue";
    public static final String GROUP_CREATED_DLQ_NAME = "group.created.dlq";
}
