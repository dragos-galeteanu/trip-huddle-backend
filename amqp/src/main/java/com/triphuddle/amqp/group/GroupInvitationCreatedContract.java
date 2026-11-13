package com.triphuddle.amqp.group;

/**
 * Queue and dead-letter queue names for {@link GroupInvitationCreatedEvent}.
 */
public class GroupInvitationCreatedContract {
    public static final String GROUP_INVITATION_CREATED_QUEUE_NAME = "group.invitation.created.queue";
    public static final String GROUP_INVITATION_CREATED_DLQ_NAME = "group.invitation.created.dlq";
}
