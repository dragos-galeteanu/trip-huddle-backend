package com.triphuddle.amqp.group;

/**
 * Queue and dead-letter queue names for {@link GroupInvitationUpdatedEvent}.
 */
public class GroupInvitationUpdatedContract {
    public static final String GROUP_INVITATION_UPDATED_QUEUE_NAME = "group.invitation.updated.queue";
    public static final String GROUP_INVITATION_UPDATED_DLQ_NAME = "group.invitation.updated.dlq";
}
