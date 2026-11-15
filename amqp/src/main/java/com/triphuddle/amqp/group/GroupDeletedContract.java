package com.triphuddle.amqp.group;

/**
 * Queue and dead-letter queue names for {@link GroupDeletedEvent}. The event goes to two queues, one per
 * consuming service, as a queue has only one consumer: the first is api-chat's, the {@code LICENSE_} one api-license's.
 */
public class GroupDeletedContract {
    public static final String GROUP_DELETED_QUEUE_NAME = "group.deleted.queue";
    public static final String GROUP_DELETED_DLQ_NAME = "group.deleted.dlq";
    public static final String GROUP_DELETED_LICENSE_QUEUE_NAME = "group.deleted.license.queue";
    public static final String GROUP_DELETED_LICENSE_DLQ_NAME = "group.deleted.license.dlq";
}
