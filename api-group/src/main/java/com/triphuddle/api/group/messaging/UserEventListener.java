package com.triphuddle.api.group.messaging;

import com.triphuddle.amqp.user.UserEmailVerifiedContract;
import com.triphuddle.amqp.user.UserEmailVerifiedEvent;
import com.triphuddle.amqp.user.UserMatchedToGroupInvitationContract;
import com.triphuddle.amqp.user.UserMatchedToGroupInvitationEvent;
import com.triphuddle.api.group.service.GroupInvitationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Turns what api-user reports about a user into the memberships of the invitations they accepted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventListener {

    private final GroupInvitationService groupInvitationService;

    @RabbitListener(
            queues = UserMatchedToGroupInvitationContract.USER_MATCHED_TO_GROUP_INVITATION_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory"
    )
    public void onUserMatchedToGroupInvitation(UserMatchedToGroupInvitationEvent event) {
        log.info("Received user matched to group invitation [groupInvitationId={}, userId={}]",
                event.groupInvitationId(), event.userId());
        groupInvitationService.linkUser(event.groupInvitationId(), event.userId());
    }

    @RabbitListener(
            queues = UserEmailVerifiedContract.USER_EMAIL_VERIFIED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory"
    )
    public void onUserEmailVerified(UserEmailVerifiedEvent event) {
        log.info("Received user email verified [userId={}]", event.userId());
        groupInvitationService.linkVerifiedEmail(event.userId(), event.email());
    }
}
