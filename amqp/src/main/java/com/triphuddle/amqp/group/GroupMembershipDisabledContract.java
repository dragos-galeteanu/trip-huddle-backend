package com.triphuddle.amqp.group;

/**
 * Queue and dead-letter queue names for {@link GroupMembershipDisabledEvent}.
 */
public class GroupMembershipDisabledContract {
    public static final String GROUP_MEMBERSHIP_DISABLED_QUEUE_NAME = "group.membership.disabled.queue";
    public static final String GROUP_MEMBERSHIP_DISABLED_DLQ_NAME = "group.membership.disabled.dlq";
}
