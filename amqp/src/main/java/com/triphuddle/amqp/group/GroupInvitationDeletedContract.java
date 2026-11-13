package com.triphuddle.amqp.group;

/**
 * Queue and dead-letter queue names for {@link GroupInvitationDeletedEvent}.
 */
public class GroupInvitationDeletedContract {
    public static final String GROUP_INVITATION_DELETED_QUEUE_NAME = "group.invitation.deleted.queue";
    public static final String GROUP_INVITATION_DELETED_DLQ_NAME = "group.invitation.deleted.dlq";
}
