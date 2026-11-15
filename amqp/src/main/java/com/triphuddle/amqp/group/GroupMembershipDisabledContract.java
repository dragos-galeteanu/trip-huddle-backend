package com.triphuddle.amqp.group;

/**
 * Queue and dead-letter queue names for {@link GroupMembershipDisabledEvent}. The event goes to three queues, one per
 * consuming service, as a queue has only one consumer: the first is api-chat's, the {@code LICENSE_} one
 * api-license's and the {@code PAYMENT_} one api-payment's.
 */
public class GroupMembershipDisabledContract {
    public static final String GROUP_MEMBERSHIP_DISABLED_QUEUE_NAME = "group.membership.disabled.queue";
    public static final String GROUP_MEMBERSHIP_DISABLED_DLQ_NAME = "group.membership.disabled.dlq";
    public static final String GROUP_MEMBERSHIP_DISABLED_LICENSE_QUEUE_NAME = "group.membership.disabled.license.queue";
    public static final String GROUP_MEMBERSHIP_DISABLED_LICENSE_DLQ_NAME = "group.membership.disabled.license.dlq";
    public static final String GROUP_MEMBERSHIP_DISABLED_PAYMENT_QUEUE_NAME = "group.membership.disabled.payment.queue";
    public static final String GROUP_MEMBERSHIP_DISABLED_PAYMENT_DLQ_NAME = "group.membership.disabled.payment.dlq";
}
