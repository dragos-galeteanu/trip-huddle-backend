package com.triphuddle.api.destination.messaging;

import com.triphuddle.amqp.license.LicenseEntitlementsChangedContract;
import com.triphuddle.amqp.license.LicenseEntitlementsChangedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionActivatedContract;
import com.triphuddle.amqp.license.LicenseSubscriptionActivatedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionEndedContract;
import com.triphuddle.amqp.license.LicenseSubscriptionEndedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionRenewedContract;
import com.triphuddle.amqp.license.LicenseSubscriptionRenewedEvent;
import com.triphuddle.api.destination.service.CopyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Follows api-license's subscriptions and entitlements, which decide who may use the service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LicenseEventListener {

    private final CopyService copyService;

    /**
     * Record a subscription that started.
     *
     * @param event the event
     */
    @RabbitListener(queues = LicenseSubscriptionActivatedContract.LICENSE_SUBSCRIPTION_ACTIVATED_DESTINATION_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onSubscriptionActivated(LicenseSubscriptionActivatedEvent event) {
        log.info("Received license subscription activated [subscriptionId={}]", event.subscriptionId());
        copyService.onSubscriptionActivated(event);
    }

    /**
     * Move a subscription on to its new period end.
     *
     * @param event the event
     */
    @RabbitListener(queues = LicenseSubscriptionRenewedContract.LICENSE_SUBSCRIPTION_RENEWED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onSubscriptionRenewed(LicenseSubscriptionRenewedEvent event) {
        log.info("Received license subscription renewed [subscriptionId={}]", event.subscriptionId());
        copyService.onSubscriptionRenewed(event);
    }

    /**
     * End a subscription's access.
     *
     * @param event the event
     */
    @RabbitListener(queues = LicenseSubscriptionEndedContract.LICENSE_SUBSCRIPTION_ENDED_DESTINATION_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onSubscriptionEnded(LicenseSubscriptionEndedEvent event) {
        log.info("Received license subscription ended [subscriptionId={}]", event.subscriptionId());
        copyService.onSubscriptionEnded(event);
    }

    /**
     * Give a license's subscriptions its new entitlements.
     *
     * @param event the event
     */
    @RabbitListener(queues = LicenseEntitlementsChangedContract.LICENSE_ENTITLEMENTS_CHANGED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onEntitlementsChanged(LicenseEntitlementsChangedEvent event) {
        log.info("Received license entitlements changed [licenseId={}]", event.licenseId());
        copyService.onEntitlementsChanged(event);
    }
}
