package com.triphuddle.amqp.group;

import java.util.List;
import java.util.Map;

/**
 * Every group exchange and the queues bound to it, one per consuming service.
 * {@code RabbitMQConfig} declares each as a durable fanout exchange with these queues bound to it.
 */
public class GroupExchanges {
    public static final Map<String, List<String>> BINDINGS = Map.of(
            GroupDisabledContract.GROUP_DISABLED_EXCHANGE_NAME, List.of(
                    GroupDisabledContract.GROUP_DISABLED_QUEUE_NAME,
                    GroupDisabledContract.GROUP_DISABLED_LICENSE_QUEUE_NAME,
                    GroupDisabledContract.GROUP_DISABLED_PAYMENT_QUEUE_NAME,
                    GroupDisabledContract.GROUP_DISABLED_DESTINATION_QUEUE_NAME),
            GroupDeletedContract.GROUP_DELETED_EXCHANGE_NAME, List.of(
                    GroupDeletedContract.GROUP_DELETED_QUEUE_NAME,
                    GroupDeletedContract.GROUP_DELETED_LICENSE_QUEUE_NAME,
                    GroupDeletedContract.GROUP_DELETED_PAYMENT_QUEUE_NAME,
                    GroupDeletedContract.GROUP_DELETED_DESTINATION_QUEUE_NAME),
            GroupMembershipCreatedContract.GROUP_MEMBERSHIP_CREATED_EXCHANGE_NAME, List.of(
                    GroupMembershipCreatedContract.GROUP_MEMBERSHIP_CREATED_QUEUE_NAME,
                    GroupMembershipCreatedContract.GROUP_MEMBERSHIP_CREATED_LICENSE_QUEUE_NAME,
                    GroupMembershipCreatedContract.GROUP_MEMBERSHIP_CREATED_PAYMENT_QUEUE_NAME,
                    GroupMembershipCreatedContract.GROUP_MEMBERSHIP_CREATED_DESTINATION_QUEUE_NAME),
            GroupMembershipUpdatedContract.GROUP_MEMBERSHIP_UPDATED_EXCHANGE_NAME, List.of(
                    GroupMembershipUpdatedContract.GROUP_MEMBERSHIP_UPDATED_QUEUE_NAME,
                    GroupMembershipUpdatedContract.GROUP_MEMBERSHIP_UPDATED_LICENSE_QUEUE_NAME,
                    GroupMembershipUpdatedContract.GROUP_MEMBERSHIP_UPDATED_PAYMENT_QUEUE_NAME,
                    GroupMembershipUpdatedContract.GROUP_MEMBERSHIP_UPDATED_DESTINATION_QUEUE_NAME),
            GroupMembershipDisabledContract.GROUP_MEMBERSHIP_DISABLED_EXCHANGE_NAME, List.of(
                    GroupMembershipDisabledContract.GROUP_MEMBERSHIP_DISABLED_QUEUE_NAME,
                    GroupMembershipDisabledContract.GROUP_MEMBERSHIP_DISABLED_LICENSE_QUEUE_NAME,
                    GroupMembershipDisabledContract.GROUP_MEMBERSHIP_DISABLED_PAYMENT_QUEUE_NAME,
                    GroupMembershipDisabledContract.GROUP_MEMBERSHIP_DISABLED_DESTINATION_QUEUE_NAME),
            GroupMembershipDeletedContract.GROUP_MEMBERSHIP_DELETED_EXCHANGE_NAME, List.of(
                    GroupMembershipDeletedContract.GROUP_MEMBERSHIP_DELETED_QUEUE_NAME,
                    GroupMembershipDeletedContract.GROUP_MEMBERSHIP_DELETED_LICENSE_QUEUE_NAME,
                    GroupMembershipDeletedContract.GROUP_MEMBERSHIP_DELETED_PAYMENT_QUEUE_NAME,
                    GroupMembershipDeletedContract.GROUP_MEMBERSHIP_DELETED_DESTINATION_QUEUE_NAME)
    );
}
