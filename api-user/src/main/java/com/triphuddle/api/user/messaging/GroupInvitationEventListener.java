package com.triphuddle.api.user.messaging;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.group.GroupInvitationAcceptedContract;
import com.triphuddle.amqp.group.GroupInvitationAcceptedEvent;
import com.triphuddle.amqp.user.UserMatchedToGroupInvitationContract;
import com.triphuddle.amqp.user.UserMatchedToGroupInvitationEvent;
import com.triphuddle.api.user.entity.User;
import com.triphuddle.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Tells api-group which user an accepted group invitation belongs to, if a verified one has that email.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupInvitationEventListener {

    private final UserRepository userRepository;
    private final RabbitMQMessageProducer messageProducer;

    @RabbitListener(
            queues = GroupInvitationAcceptedContract.GROUP_INVITATION_ACCEPTED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory"
    )
    public void onGroupInvitationAccepted(GroupInvitationAcceptedEvent event) {
        log.info("Received group invitation accepted [groupInvitationId={}]", event.groupInvitationId());

        Optional<User> user = userRepository.findByEmailIgnoreCase(event.email());

        if (user.isEmpty() || !user.get().isEmailVerified()) {
            log.info("No verified user for accepted group invitation yet [groupInvitationId={}]",
                    event.groupInvitationId());
            return;
        }

        messageProducer.publish(
                new UserMatchedToGroupInvitationEvent(event.groupInvitationId(), user.get().getId()),
                "",
                UserMatchedToGroupInvitationContract.USER_MATCHED_TO_GROUP_INVITATION_QUEUE_NAME
        );
        log.info("Event published for user matched to group invitation [groupInvitationId={}, userId={}]",
                event.groupInvitationId(), user.get().getId());
    }
}
