package com.triphuddle.amqp.group;

/**
 * Names for {@link GroupMembershipCreatedEvent}. It is published once to a fanout exchange, and each consuming service
 * reads its own queue bound to it: the plain queue is api-chat's, {@code LICENSE_} api-license's, {@code PAYMENT_}
 * api-payment's and {@code DESTINATION_} api-destination's.
 */
public class GroupMembershipCreatedContract {
    public static final String GROUP_MEMBERSHIP_CREATED_EXCHANGE_NAME = "group.membership.created.exchange";
    public static final String GROUP_MEMBERSHIP_CREATED_QUEUE_NAME = "group.membership.created.queue";
    public static final String GROUP_MEMBERSHIP_CREATED_DLQ_NAME = "group.membership.created.dlq";
    public static final String GROUP_MEMBERSHIP_CREATED_LICENSE_QUEUE_NAME = "group.membership.created.license.queue";
    public static final String GROUP_MEMBERSHIP_CREATED_LICENSE_DLQ_NAME = "group.membership.created.license.dlq";
    public static final String GROUP_MEMBERSHIP_CREATED_PAYMENT_QUEUE_NAME = "group.membership.created.payment.queue";
    public static final String GROUP_MEMBERSHIP_CREATED_PAYMENT_DLQ_NAME = "group.membership.created.payment.dlq";
    public static final String GROUP_MEMBERSHIP_CREATED_DESTINATION_QUEUE_NAME =
            "group.membership.created.destination.queue";
    public static final String GROUP_MEMBERSHIP_CREATED_DESTINATION_DLQ_NAME =
            "group.membership.created.destination.dlq";
}
