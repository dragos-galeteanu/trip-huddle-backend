package com.triphuddle.api.chat.messaging;

import com.triphuddle.amqp.user.UserDeletedContract;
import com.triphuddle.amqp.user.UserDeletedEvent;
import com.triphuddle.amqp.user.UserDisabledContract;
import com.triphuddle.amqp.user.UserDisabledEvent;
import com.triphuddle.amqp.user.UserRegisteredContract;
import com.triphuddle.amqp.user.UserRegisteredEvent;
import com.triphuddle.api.chat.service.ChatUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the chat users in step with what api-user reports about registrations, disables and deletes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventListener {

    private final ChatUserService chatUserService;

    /**
     * Record a newly registered user.
     *
     * @param event the new user
     */
    @RabbitListener(queues = UserRegisteredContract.USER_REGISTERED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onUserRegistered(UserRegisteredEvent event) {
        log.info("Received user registered [userId={}]", event.userId());
        chatUserService.register(event.userId());
    }

    /**
     * Stop a disabled user from chatting.
     *
     * @param event the disabled user
     */
    @RabbitListener(queues = UserDisabledContract.USER_DISABLED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onUserDisabled(UserDisabledEvent event) {
        log.info("Received user disabled [userId={}]", event.userId());
        chatUserService.disable(event.userId());
    }

    /**
     * Stop a deleted user from chatting.
     *
     * @param event the deleted user
     */
    @RabbitListener(queues = UserDeletedContract.USER_DELETED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onUserDeleted(UserDeletedEvent event) {
        log.info("Received user deleted [userId={}]", event.userId());
        chatUserService.delete(event.userId());
    }
}
