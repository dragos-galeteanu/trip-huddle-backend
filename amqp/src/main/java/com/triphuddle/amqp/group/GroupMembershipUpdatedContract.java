package com.triphuddle.amqp.group;

/**
 * Queue and dead-letter queue names for {@link GroupMembershipUpdatedEvent}. The event goes to three queues, one per
 * consuming service, as a queue has only one consumer: the first is api-chat's, the {@code LICENSE_} one
 * api-license's and the {@code PAYMENT_} one api-payment's.
 */
public class GroupMembershipUpdatedContract {
    public static final String GROUP_MEMBERSHIP_UPDATED_QUEUE_NAME = "group.membership.updated.queue";
    public static final String GROUP_MEMBERSHIP_UPDATED_DLQ_NAME = "group.membership.updated.dlq";
    public static final String GROUP_MEMBERSHIP_UPDATED_LICENSE_QUEUE_NAME = "group.membership.updated.license.queue";
    public static final String GROUP_MEMBERSHIP_UPDATED_LICENSE_DLQ_NAME = "group.membership.updated.license.dlq";
    public static final String GROUP_MEMBERSHIP_UPDATED_PAYMENT_QUEUE_NAME = "group.membership.updated.payment.queue";
    public static final String GROUP_MEMBERSHIP_UPDATED_PAYMENT_DLQ_NAME = "group.membership.updated.payment.dlq";
}
