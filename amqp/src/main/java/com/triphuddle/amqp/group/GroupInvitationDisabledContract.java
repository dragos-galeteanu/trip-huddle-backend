package com.triphuddle.amqp.group;

/**
 * Queue and dead-letter queue names for {@link GroupInvitationDisabledEvent}.
 */
public class GroupInvitationDisabledContract {
    public static final String GROUP_INVITATION_DISABLED_QUEUE_NAME = "group.invitation.disabled.queue";
    public static final String GROUP_INVITATION_DISABLED_DLQ_NAME = "group.invitation.disabled.dlq";
}
