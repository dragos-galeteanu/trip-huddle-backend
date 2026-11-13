package com.triphuddle.amqp.group;

import java.util.List;

/** Every queue the group contracts define, declared on the broker by {@code RabbitMQConfig}. */
public class GroupQueues {
    public static final List<String> QUEUE_NAMES = List.of(
            GroupCreatedContract.GROUP_CREATED_QUEUE_NAME,
            GroupCreatedContract.GROUP_CREATED_DLQ_NAME,
            GroupUpdatedContract.GROUP_UPDATED_QUEUE_NAME,
            GroupUpdatedContract.GROUP_UPDATED_DLQ_NAME,
            GroupDisabledContract.GROUP_DISABLED_QUEUE_NAME,
            GroupDisabledContract.GROUP_DISABLED_DLQ_NAME,
            GroupDeletedContract.GROUP_DELETED_QUEUE_NAME,
            GroupDeletedContract.GROUP_DELETED_DLQ_NAME,
            GroupMembershipCreatedContract.GROUP_MEMBERSHIP_CREATED_QUEUE_NAME,
            GroupMembershipCreatedContract.GROUP_MEMBERSHIP_CREATED_DLQ_NAME,
            GroupMembershipUpdatedContract.GROUP_MEMBERSHIP_UPDATED_QUEUE_NAME,
            GroupMembershipUpdatedContract.GROUP_MEMBERSHIP_UPDATED_DLQ_NAME,
            GroupMembershipDisabledContract.GROUP_MEMBERSHIP_DISABLED_QUEUE_NAME,
            GroupMembershipDisabledContract.GROUP_MEMBERSHIP_DISABLED_DLQ_NAME,
            GroupMembershipDeletedContract.GROUP_MEMBERSHIP_DELETED_QUEUE_NAME,
            GroupMembershipDeletedContract.GROUP_MEMBERSHIP_DELETED_DLQ_NAME,
            GroupInvitationCreatedContract.GROUP_INVITATION_CREATED_QUEUE_NAME,
            GroupInvitationCreatedContract.GROUP_INVITATION_CREATED_DLQ_NAME,
            GroupInvitationUpdatedContract.GROUP_INVITATION_UPDATED_QUEUE_NAME,
            GroupInvitationUpdatedContract.GROUP_INVITATION_UPDATED_DLQ_NAME,
            GroupInvitationDisabledContract.GROUP_INVITATION_DISABLED_QUEUE_NAME,
            GroupInvitationDisabledContract.GROUP_INVITATION_DISABLED_DLQ_NAME,
            GroupInvitationDeletedContract.GROUP_INVITATION_DELETED_QUEUE_NAME,
            GroupInvitationDeletedContract.GROUP_INVITATION_DELETED_DLQ_NAME,
            GroupInvitationAcceptedContract.GROUP_INVITATION_ACCEPTED_QUEUE_NAME,
            GroupInvitationAcceptedContract.GROUP_INVITATION_ACCEPTED_DLQ_NAME,
            GroupInvitationRejectedContract.GROUP_INVITATION_REJECTED_QUEUE_NAME,
            GroupInvitationRejectedContract.GROUP_INVITATION_REJECTED_DLQ_NAME,
            GroupInvitationCanceledContract.GROUP_INVITATION_CANCELED_QUEUE_NAME,
            GroupInvitationCanceledContract.GROUP_INVITATION_CANCELED_DLQ_NAME
    );
}
