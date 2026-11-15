package com.triphuddle.amqp.group;

/**
 * Queue and dead-letter queue names for {@link GroupMembershipDeletedEvent}. The event goes to three queues, one per
 * consuming service, as a queue has only one consumer: the first is api-chat's, the {@code LICENSE_} one
 * api-license's and the {@code PAYMENT_} one api-payment's.
 */
public class GroupMembershipDeletedContract {
    public static final String GROUP_MEMBERSHIP_DELETED_QUEUE_NAME = "group.membership.deleted.queue";
    public static final String GROUP_MEMBERSHIP_DELETED_DLQ_NAME = "group.membership.deleted.dlq";
    public static final String GROUP_MEMBERSHIP_DELETED_LICENSE_QUEUE_NAME = "group.membership.deleted.license.queue";
    public static final String GROUP_MEMBERSHIP_DELETED_LICENSE_DLQ_NAME = "group.membership.deleted.license.dlq";
    public static final String GROUP_MEMBERSHIP_DELETED_PAYMENT_QUEUE_NAME = "group.membership.deleted.payment.queue";
    public static final String GROUP_MEMBERSHIP_DELETED_PAYMENT_DLQ_NAME = "group.membership.deleted.payment.dlq";
}
