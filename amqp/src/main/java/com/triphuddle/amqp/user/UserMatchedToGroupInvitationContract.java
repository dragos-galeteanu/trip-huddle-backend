package com.triphuddle.amqp.user;

/**
 * Queue and dead-letter queue names for {@link UserMatchedToGroupInvitationEvent}.
 */
public class UserMatchedToGroupInvitationContract {
    public static final String USER_MATCHED_TO_GROUP_INVITATION_QUEUE_NAME = "user.matched-to-group-invitation.queue";
    public static final String USER_MATCHED_TO_GROUP_INVITATION_DLQ_NAME = "user.matched-to-group-invitation.dlq";
}
