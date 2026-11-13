package com.triphuddle.amqp.group;

/**
 * Queue and dead-letter queue names for {@link GroupInvitationRejectedEvent}.
 */
public class GroupInvitationRejectedContract {
    public static final String GROUP_INVITATION_REJECTED_QUEUE_NAME = "group.invitation.rejected.queue";
    public static final String GROUP_INVITATION_REJECTED_DLQ_NAME = "group.invitation.rejected.dlq";
}
