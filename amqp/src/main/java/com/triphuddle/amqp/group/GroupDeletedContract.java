package com.triphuddle.amqp.group;

/**
 * Names for {@link GroupDeletedEvent}. It is published once to a fanout exchange, and each consuming service reads its
 * own queue bound to it: the plain queue is api-chat's, {@code LICENSE_} api-license's, {@code PAYMENT_} api-payment's
 * and {@code DESTINATION_} api-destination's.
 */
public class GroupDeletedContract {
    public static final String GROUP_DELETED_EXCHANGE_NAME = "group.deleted.exchange";
    public static final String GROUP_DELETED_QUEUE_NAME = "group.deleted.queue";
    public static final String GROUP_DELETED_DLQ_NAME = "group.deleted.dlq";
    public static final String GROUP_DELETED_LICENSE_QUEUE_NAME = "group.deleted.license.queue";
    public static final String GROUP_DELETED_LICENSE_DLQ_NAME = "group.deleted.license.dlq";
    public static final String GROUP_DELETED_PAYMENT_QUEUE_NAME = "group.deleted.payment.queue";
    public static final String GROUP_DELETED_PAYMENT_DLQ_NAME = "group.deleted.payment.dlq";
    public static final String GROUP_DELETED_DESTINATION_QUEUE_NAME = "group.deleted.destination.queue";
    public static final String GROUP_DELETED_DESTINATION_DLQ_NAME = "group.deleted.destination.dlq";
}
