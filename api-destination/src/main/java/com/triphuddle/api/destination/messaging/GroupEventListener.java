package com.triphuddle.api.destination.messaging;

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
import com.triphuddle.api.destination.service.CopyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Follows api-group's memberships on its destination queues, as a group's license applies to its members.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupEventListener {

    private final CopyService copyService;

    /**
     * Record a new membership.
     *
     * @param event the event
     */
    @RabbitListener(queues = GroupMembershipCreatedContract.GROUP_MEMBERSHIP_CREATED_DESTINATION_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onMembershipCreated(GroupMembershipCreatedEvent event) {
        log.info("Received group membership created [groupMembershipId={}]", event.groupMembershipId());
        copyService.onMembershipChanged(event.groupMembershipId(), event.groupId(), event.userId(), event.role());
    }

    /**
     * Follow a change of role.
     *
     * @param event the event
     */
    @RabbitListener(queues = GroupMembershipUpdatedContract.GROUP_MEMBERSHIP_UPDATED_DESTINATION_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onMembershipUpdated(GroupMembershipUpdatedEvent event) {
        log.info("Received group membership updated [groupMembershipId={}]", event.groupMembershipId());
        copyService.onMembershipChanged(event.groupMembershipId(), event.groupId(), event.userId(), event.role());
    }

    /**
     * Forget a disabled membership.
     *
     * @param event the event
     */
    @RabbitListener(queues = GroupMembershipDisabledContract.GROUP_MEMBERSHIP_DISABLED_DESTINATION_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onMembershipDisabled(GroupMembershipDisabledEvent event) {
        log.info("Received group membership disabled [groupMembershipId={}]", event.groupMembershipId());
        copyService.onMembershipRemoved(event.groupMembershipId());
    }

    /**
     * Forget a deleted membership.
     *
     * @param event the event
     */
    @RabbitListener(queues = GroupMembershipDeletedContract.GROUP_MEMBERSHIP_DELETED_DESTINATION_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onMembershipDeleted(GroupMembershipDeletedEvent event) {
        log.info("Received group membership deleted [groupMembershipId={}]", event.groupMembershipId());
        copyService.onMembershipRemoved(event.groupMembershipId());
    }

    /**
     * Forget the members of a disabled group.
     *
     * @param event the event
     */
    @RabbitListener(queues = GroupDisabledContract.GROUP_DISABLED_DESTINATION_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onGroupDisabled(GroupDisabledEvent event) {
        log.info("Received group disabled [groupId={}]", event.groupId());
        copyService.onGroupRemoved(event.groupId());
    }

    /**
     * Forget the members of a deleted group.
     *
     * @param event the event
     */
    @RabbitListener(queues = GroupDeletedContract.GROUP_DELETED_DESTINATION_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onGroupDeleted(GroupDeletedEvent event) {
        log.info("Received group deleted [groupId={}]", event.groupId());
        copyService.onGroupRemoved(event.groupId());
    }
}
