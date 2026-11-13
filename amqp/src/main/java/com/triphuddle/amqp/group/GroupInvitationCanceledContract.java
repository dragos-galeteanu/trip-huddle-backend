package com.triphuddle.amqp.group;

/**
 * Queue and dead-letter queue names for {@link GroupInvitationCanceledEvent}.
 */
public class GroupInvitationCanceledContract {
    public static final String GROUP_INVITATION_CANCELED_QUEUE_NAME = "group.invitation.canceled.queue";
    public static final String GROUP_INVITATION_CANCELED_DLQ_NAME = "group.invitation.canceled.dlq";
}
