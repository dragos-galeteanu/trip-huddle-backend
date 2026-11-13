package com.triphuddle.amqp.group;

/**
 * Queue and dead-letter queue names for {@link GroupDisabledEvent}.
 */
public class GroupDisabledContract {
    public static final String GROUP_DISABLED_QUEUE_NAME = "group.disabled.queue";
    public static final String GROUP_DISABLED_DLQ_NAME = "group.disabled.dlq";
}
