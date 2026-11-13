package com.triphuddle.amqp.group;

/**
 * Queue and dead-letter queue names for {@link GroupMembershipUpdatedEvent}.
 */
public class GroupMembershipUpdatedContract {
    public static final String GROUP_MEMBERSHIP_UPDATED_QUEUE_NAME = "group.membership.updated.queue";
    public static final String GROUP_MEMBERSHIP_UPDATED_DLQ_NAME = "group.membership.updated.dlq";
}
