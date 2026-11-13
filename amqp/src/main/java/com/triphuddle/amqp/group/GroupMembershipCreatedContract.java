package com.triphuddle.amqp.group;

/**
 * Queue and dead-letter queue names for {@link GroupMembershipCreatedEvent}.
 */
public class GroupMembershipCreatedContract {
    public static final String GROUP_MEMBERSHIP_CREATED_QUEUE_NAME = "group.membership.created.queue";
    public static final String GROUP_MEMBERSHIP_CREATED_DLQ_NAME = "group.membership.created.dlq";
}
