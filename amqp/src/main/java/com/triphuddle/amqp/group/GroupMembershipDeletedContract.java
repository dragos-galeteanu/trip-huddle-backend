package com.triphuddle.amqp.group;

/**
 * Queue and dead-letter queue names for {@link GroupMembershipDeletedEvent}.
 */
public class GroupMembershipDeletedContract {
    public static final String GROUP_MEMBERSHIP_DELETED_QUEUE_NAME = "group.membership.deleted.queue";
    public static final String GROUP_MEMBERSHIP_DELETED_DLQ_NAME = "group.membership.deleted.dlq";
}
