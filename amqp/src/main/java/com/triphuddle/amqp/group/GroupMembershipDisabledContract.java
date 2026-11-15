package com.triphuddle.amqp.group;

/**
 * Queue and dead-letter queue names for {@link GroupMembershipDisabledEvent}. The event goes to two queues, one per
 * consuming service, as a queue has only one consumer: the first is api-chat's, the {@code LICENSE_} one api-license's.
 */
public class GroupMembershipDisabledContract {
    public static final String GROUP_MEMBERSHIP_DISABLED_QUEUE_NAME = "group.membership.disabled.queue";
    public static final String GROUP_MEMBERSHIP_DISABLED_DLQ_NAME = "group.membership.disabled.dlq";
    public static final String GROUP_MEMBERSHIP_DISABLED_LICENSE_QUEUE_NAME = "group.membership.disabled.license.queue";
    public static final String GROUP_MEMBERSHIP_DISABLED_LICENSE_DLQ_NAME = "group.membership.disabled.license.dlq";
}
