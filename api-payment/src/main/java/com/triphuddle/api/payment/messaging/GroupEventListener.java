package com.triphuddle.api.payment.messaging;

import com.triphuddle.amqp.group.GroupDeletedContract;
import com.triphuddle.amqp.group.GroupDeletedEvent;
import com.triphuddle.amqp.group.GroupDisabledContract;
import com.triphuddle.amqp.group.GroupDisabledEvent;
import com.triphuddle.amqp.group.GroupMembershipCreatedContract;
import com.triphuddle.amqp.group.GroupMembershipCreatedEvent;
import com.triphuddle.amqp.group.GroupMembershipDeletedContract;
import com.triphuddle.amqp.group.GroupMembershipDeletedEvent;
import com.triphuddle.amqp.group.GroupMembershipDisabledContract;
import com.triphuddle.amqp.group.GroupMembershipDisabledEvent;
import com.triphuddle.amqp.group.GroupMembershipUpdatedContract;
import com.triphuddle.amqp.group.GroupMembershipUpdatedEvent;
import com.triphuddle.api.payment.service.GroupAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Follows what api-group reports on its payment queues: who administers which group, and groups that go away.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupEventListener {

    private final GroupAdminService groupAdminService;

    /**
     * Record a new membership.
     *
     * @param event the new membership
     */
    @RabbitListener(queues = GroupMembershipCreatedContract.GROUP_MEMBERSHIP_CREATED_PAYMENT_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onMembershipCreated(GroupMembershipCreatedEvent event) {
        log.info("Received group membership created [groupMembershipId={}]", event.groupMembershipId());
        groupAdminService.onMembershipChanged(event.groupMembershipId(), event.groupId(), event.userId(), event.role());
    }

    /**
     * Follow a change of role.
     *
     * @param event the updated membership
     */
    @RabbitListener(queues = GroupMembershipUpdatedContract.GROUP_MEMBERSHIP_UPDATED_PAYMENT_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onMembershipUpdated(GroupMembershipUpdatedEvent event) {
        log.info("Received group membership updated [groupMembershipId={}]", event.groupMembershipId());
        groupAdminService.onMembershipChanged(event.groupMembershipId(), event.groupId(), event.userId(), event.role());
    }

    /**
     * Forget a disabled membership.
     *
     * @param event the disabled membership
     */
    @RabbitListener(queues = GroupMembershipDisabledContract.GROUP_MEMBERSHIP_DISABLED_PAYMENT_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onMembershipDisabled(GroupMembershipDisabledEvent event) {
        log.info("Received group membership disabled [groupMembershipId={}]", event.groupMembershipId());
        groupAdminService.onMembershipRemoved(event.groupMembershipId());
    }

    /**
     * Forget a deleted membership.
     *
     * @param event the deleted membership
     */
    @RabbitListener(queues = GroupMembershipDeletedContract.GROUP_MEMBERSHIP_DELETED_PAYMENT_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onMembershipDeleted(GroupMembershipDeletedEvent event) {
        log.info("Received group membership deleted [groupMembershipId={}]", event.groupMembershipId());
        groupAdminService.onMembershipRemoved(event.groupMembershipId());
    }

    /**
     * Forget the admins of a disabled group.
     *
     * @param event the disabled group
     */
    @RabbitListener(queues = GroupDisabledContract.GROUP_DISABLED_PAYMENT_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onGroupDisabled(GroupDisabledEvent event) {
        log.info("Received group disabled [groupId={}]", event.groupId());
        groupAdminService.onGroupRemoved(event.groupId());
    }

    /**
     * Forget the admins of a deleted group.
     *
     * @param event the deleted group
     */
    @RabbitListener(queues = GroupDeletedContract.GROUP_DELETED_PAYMENT_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onGroupDeleted(GroupDeletedEvent event) {
        log.info("Received group deleted [groupId={}]", event.groupId());
        groupAdminService.onGroupRemoved(event.groupId());
    }
}
