package com.triphuddle.amqp.group;

/**
 * Names for {@link GroupMembershipUpdatedEvent}. It is published once to a fanout exchange, and each consuming service
 * reads its own queue bound to it: the plain queue is api-chat's, {@code LICENSE_} api-license's, {@code PAYMENT_}
 * api-payment's and {@code DESTINATION_} api-destination's.
 */
public class GroupMembershipUpdatedContract {
    public static final String GROUP_MEMBERSHIP_UPDATED_EXCHANGE_NAME = "group.membership.updated.exchange";
    public static final String GROUP_MEMBERSHIP_UPDATED_QUEUE_NAME = "group.membership.updated.queue";
    public static final String GROUP_MEMBERSHIP_UPDATED_DLQ_NAME = "group.membership.updated.dlq";
    public static final String GROUP_MEMBERSHIP_UPDATED_LICENSE_QUEUE_NAME = "group.membership.updated.license.queue";
    public static final String GROUP_MEMBERSHIP_UPDATED_LICENSE_DLQ_NAME = "group.membership.updated.license.dlq";
    public static final String GROUP_MEMBERSHIP_UPDATED_PAYMENT_QUEUE_NAME = "group.membership.updated.payment.queue";
    public static final String GROUP_MEMBERSHIP_UPDATED_PAYMENT_DLQ_NAME = "group.membership.updated.payment.dlq";
    public static final String GROUP_MEMBERSHIP_UPDATED_DESTINATION_QUEUE_NAME =
            "group.membership.updated.destination.queue";
    public static final String GROUP_MEMBERSHIP_UPDATED_DESTINATION_DLQ_NAME =
            "group.membership.updated.destination.dlq";
}
