package com.triphuddle.api.payment.messaging;

import com.triphuddle.amqp.user.UserContactUpdatedContract;
import com.triphuddle.amqp.user.UserContactUpdatedEvent;
import com.triphuddle.amqp.user.UserDeletedContract;
import com.triphuddle.amqp.user.UserDeletedEvent;
import com.triphuddle.amqp.user.UserDisabledContract;
import com.triphuddle.amqp.user.UserDisabledEvent;
import com.triphuddle.api.payment.service.StripeBillingService;
import com.triphuddle.api.payment.service.UserContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Follows api-user's contact changes and the users it disables or deletes, whose paid subscriptions stop at the
 * end of their period.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventListener {

    private final UserContactService userContactService;
    private final StripeBillingService stripeBillingService;

    /**
     * Record a user's contact.
     *
     * @param event the contact
     */
    @RabbitListener(queues = UserContactUpdatedContract.USER_CONTACT_UPDATED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onContactUpdated(UserContactUpdatedEvent event) {
        log.info("Received user contact updated [userId={}]", event.userId());
        userContactService.onContactUpdated(event);
    }

    /**
     * Mark a user disabled.
     *
     * @param event the disabled user
     */
    @RabbitListener(queues = UserDisabledContract.USER_DISABLED_PAYMENT_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onUserDisabled(UserDisabledEvent event) {
        log.info("Received user disabled [userId={}]", event.userId());
        userContactService.onUserDisabled(event.userId());
        stripeBillingService.onUserRemoved(event.userId());
    }

    /**
     * Mark a user deleted.
     *
     * @param event the deleted user
     */
    @RabbitListener(queues = UserDeletedContract.USER_DELETED_PAYMENT_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onUserDeleted(UserDeletedEvent event) {
        log.info("Received user deleted [userId={}]", event.userId());
        userContactService.onUserDeleted(event.userId());
        stripeBillingService.onUserRemoved(event.userId());
    }
}
