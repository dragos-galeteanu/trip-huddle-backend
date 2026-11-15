package com.triphuddle.amqp.group;

/**
 * Queue and dead-letter queue names for {@link GroupMembershipCreatedEvent}. The event goes to two queues, one per
 * consuming service, as a queue has only one consumer: the first is api-chat's, the {@code LICENSE_} one api-license's.
 */
public class GroupMembershipCreatedContract {
    public static final String GROUP_MEMBERSHIP_CREATED_QUEUE_NAME = "group.membership.created.queue";
    public static final String GROUP_MEMBERSHIP_CREATED_DLQ_NAME = "group.membership.created.dlq";
    public static final String GROUP_MEMBERSHIP_CREATED_LICENSE_QUEUE_NAME = "group.membership.created.license.queue";
    public static final String GROUP_MEMBERSHIP_CREATED_LICENSE_DLQ_NAME = "group.membership.created.license.dlq";
}
