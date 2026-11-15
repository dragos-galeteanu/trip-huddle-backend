package com.triphuddle.amqp.group;

/**
 * Queue and dead-letter queue names for {@link GroupDisabledEvent}. The event goes to two queues, one per
 * consuming service, as a queue has only one consumer: the first is api-chat's, the {@code LICENSE_} one api-license's.
 */
public class GroupDisabledContract {
    public static final String GROUP_DISABLED_QUEUE_NAME = "group.disabled.queue";
    public static final String GROUP_DISABLED_DLQ_NAME = "group.disabled.dlq";
    public static final String GROUP_DISABLED_LICENSE_QUEUE_NAME = "group.disabled.license.queue";
    public static final String GROUP_DISABLED_LICENSE_DLQ_NAME = "group.disabled.license.dlq";
}
