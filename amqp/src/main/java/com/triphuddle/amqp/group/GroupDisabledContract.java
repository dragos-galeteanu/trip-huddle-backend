package com.triphuddle.amqp.group;

/**
 * Names for {@link GroupDisabledEvent}. It is published once to a fanout exchange, and each consuming service reads
 * its own queue bound to it: the plain queue is api-chat's, {@code LICENSE_} api-license's, {@code PAYMENT_}
 * api-payment's and {@code DESTINATION_} api-destination's.
 */
public class GroupDisabledContract {
    public static final String GROUP_DISABLED_EXCHANGE_NAME = "group.disabled.exchange";
    public static final String GROUP_DISABLED_QUEUE_NAME = "group.disabled.queue";
    public static final String GROUP_DISABLED_DLQ_NAME = "group.disabled.dlq";
    public static final String GROUP_DISABLED_LICENSE_QUEUE_NAME = "group.disabled.license.queue";
    public static final String GROUP_DISABLED_LICENSE_DLQ_NAME = "group.disabled.license.dlq";
    public static final String GROUP_DISABLED_PAYMENT_QUEUE_NAME = "group.disabled.payment.queue";
    public static final String GROUP_DISABLED_PAYMENT_DLQ_NAME = "group.disabled.payment.dlq";
    public static final String GROUP_DISABLED_DESTINATION_QUEUE_NAME = "group.disabled.destination.queue";
    public static final String GROUP_DISABLED_DESTINATION_DLQ_NAME = "group.disabled.destination.dlq";
}
