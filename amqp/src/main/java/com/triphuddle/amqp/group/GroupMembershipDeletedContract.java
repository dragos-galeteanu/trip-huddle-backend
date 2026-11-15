package com.triphuddle.amqp.group;

/**
 * Names for {@link GroupMembershipDeletedEvent}. It is published once to a fanout exchange, and each consuming service
 * reads its own queue bound to it: the plain queue is api-chat's, {@code LICENSE_} api-license's, {@code PAYMENT_}
 * api-payment's and {@code DESTINATION_} api-destination's.
 */
public class GroupMembershipDeletedContract {
    public static final String GROUP_MEMBERSHIP_DELETED_EXCHANGE_NAME = "group.membership.deleted.exchange";
    public static final String GROUP_MEMBERSHIP_DELETED_QUEUE_NAME = "group.membership.deleted.queue";
    public static final String GROUP_MEMBERSHIP_DELETED_DLQ_NAME = "group.membership.deleted.dlq";
    public static final String GROUP_MEMBERSHIP_DELETED_LICENSE_QUEUE_NAME = "group.membership.deleted.license.queue";
    public static final String GROUP_MEMBERSHIP_DELETED_LICENSE_DLQ_NAME = "group.membership.deleted.license.dlq";
    public static final String GROUP_MEMBERSHIP_DELETED_PAYMENT_QUEUE_NAME = "group.membership.deleted.payment.queue";
    public static final String GROUP_MEMBERSHIP_DELETED_PAYMENT_DLQ_NAME = "group.membership.deleted.payment.dlq";
    public static final String GROUP_MEMBERSHIP_DELETED_DESTINATION_QUEUE_NAME =
            "group.membership.deleted.destination.queue";
    public static final String GROUP_MEMBERSHIP_DELETED_DESTINATION_DLQ_NAME =
            "group.membership.deleted.destination.dlq";
}
