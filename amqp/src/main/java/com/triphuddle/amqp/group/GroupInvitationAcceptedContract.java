package com.triphuddle.amqp.group;

/**
 * Queue and dead-letter queue names for {@link GroupInvitationAcceptedEvent}.
 */
public class GroupInvitationAcceptedContract {
    public static final String GROUP_INVITATION_ACCEPTED_QUEUE_NAME = "group.invitation.accepted.queue";
    public static final String GROUP_INVITATION_ACCEPTED_DLQ_NAME = "group.invitation.accepted.dlq";
}
