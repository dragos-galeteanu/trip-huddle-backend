package com.triphuddle.api.chat.messaging;

import com.triphuddle.amqp.group.GroupCreatedContract;
import com.triphuddle.amqp.group.GroupCreatedEvent;
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
import com.triphuddle.amqp.group.GroupUpdatedContract;
import com.triphuddle.amqp.group.GroupUpdatedEvent;
import com.triphuddle.api.chat.service.GroupConversationSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Turns what api-group reports about groups and memberships into group chats and their participants.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupEventListener {

    private final GroupConversationSyncService groupConversationSyncService;

    /**
     * Create the chat of a new group.
     *
     * @param event the new group
     */
    @RabbitListener(queues = GroupCreatedContract.GROUP_CREATED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onGroupCreated(GroupCreatedEvent event) {
        log.info("Received group created [groupId={}]", event.groupId());
        groupConversationSyncService.onGroupCreated(event.groupId(), event.name());
    }

    /**
     * Follow a rename of a group.
     *
     * @param event the updated group
     */
    @RabbitListener(queues = GroupUpdatedContract.GROUP_UPDATED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onGroupUpdated(GroupUpdatedEvent event) {
        log.info("Received group updated [groupId={}]", event.groupId());
        groupConversationSyncService.onGroupUpdated(event.groupId(), event.name());
    }

    /**
     * Make the chat of a disabled group read-only.
     *
     * @param event the disabled group
     */
    @RabbitListener(queues = GroupDisabledContract.GROUP_DISABLED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onGroupDisabled(GroupDisabledEvent event) {
        log.info("Received group disabled [groupId={}]", event.groupId());
        groupConversationSyncService.onGroupClosed(event.groupId());
    }

    /**
     * Make the chat of a deleted group read-only.
     *
     * @param event the deleted group
     */
    @RabbitListener(queues = GroupDeletedContract.GROUP_DELETED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onGroupDeleted(GroupDeletedEvent event) {
        log.info("Received group deleted [groupId={}]", event.groupId());
        groupConversationSyncService.onGroupClosed(event.groupId());
    }

    /**
     * Add a new member to the group's chat.
     *
     * @param event the new membership
     */
    @RabbitListener(queues = GroupMembershipCreatedContract.GROUP_MEMBERSHIP_CREATED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onMembershipCreated(GroupMembershipCreatedEvent event) {
        log.info("Received group membership created [groupId={}, userId={}]", event.groupId(), event.userId());
        groupConversationSyncService.onMemberJoined(event.groupId(), event.userId(), event.role());
    }

    /**
     * Follow a member's change of role.
     *
     * @param event the updated membership
     */
    @RabbitListener(queues = GroupMembershipUpdatedContract.GROUP_MEMBERSHIP_UPDATED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onMembershipUpdated(GroupMembershipUpdatedEvent event) {
        log.info("Received group membership updated [groupId={}, userId={}]", event.groupId(), event.userId());
        groupConversationSyncService.onMemberRoleChanged(event.groupId(), event.userId(), event.role());
    }

    /**
     * Mark a member whose membership was disabled as having left the chat.
     *
     * @param event the disabled membership
     */
    @RabbitListener(queues = GroupMembershipDisabledContract.GROUP_MEMBERSHIP_DISABLED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onMembershipDisabled(GroupMembershipDisabledEvent event) {
        log.info("Received group membership disabled [groupId={}, userId={}]", event.groupId(), event.userId());
        groupConversationSyncService.onMemberLeft(event.groupId(), event.userId());
    }

    /**
     * Mark a member who left or was removed as having left the chat.
     *
     * @param event the deleted membership
     */
    @RabbitListener(queues = GroupMembershipDeletedContract.GROUP_MEMBERSHIP_DELETED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onMembershipDeleted(GroupMembershipDeletedEvent event) {
        log.info("Received group membership deleted [groupId={}, userId={}]", event.groupId(), event.userId());
        groupConversationSyncService.onMemberLeft(event.groupId(), event.userId());
    }
}
