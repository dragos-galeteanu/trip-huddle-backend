package com.triphuddle.api.license.messaging;

import com.triphuddle.amqp.payment.PaymentSubscriptionActivatedContract;
import com.triphuddle.amqp.payment.PaymentSubscriptionActivatedEvent;
import com.triphuddle.amqp.payment.PaymentSubscriptionEndedContract;
import com.triphuddle.amqp.payment.PaymentSubscriptionEndedEvent;
import com.triphuddle.amqp.payment.PaymentSubscriptionPastDueContract;
import com.triphuddle.amqp.payment.PaymentSubscriptionPastDueEvent;
import com.triphuddle.amqp.payment.PaymentSubscriptionRenewalChangedContract;
import com.triphuddle.amqp.payment.PaymentSubscriptionRenewalChangedEvent;
import com.triphuddle.amqp.payment.PaymentSubscriptionRenewedContract;
import com.triphuddle.amqp.payment.PaymentSubscriptionRenewedEvent;
import com.triphuddle.api.license.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Follows api-payment: first payments, renewals, failed renewals, ends and renewal changes from Stripe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final SubscriptionService subscriptionService;

    /**
     * Activate a subscription that was paid at checkout.
     *
     * @param event the payment
     */
    @RabbitListener(queues = PaymentSubscriptionActivatedContract.PAYMENT_SUBSCRIPTION_ACTIVATED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onSubscriptionActivated(PaymentSubscriptionActivatedEvent event) {
        log.info("Received payment subscription activated [subscriptionId={}, stripeSubscriptionId={}]",
                event.licenseSubscriptionId(), event.stripeSubscriptionId());
        subscriptionService.activatePaid(event.licenseSubscriptionId(), event.stripeSubscriptionId(),
                event.periodStart(), event.periodEnd());
    }

    /**
     * Move a subscription on to the period Stripe charged.
     *
     * @param event the renewal
     */
    @RabbitListener(queues = PaymentSubscriptionRenewedContract.PAYMENT_SUBSCRIPTION_RENEWED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onSubscriptionRenewed(PaymentSubscriptionRenewedEvent event) {
        log.info("Received payment subscription renewed [subscriptionId={}, periodEnd={}]",
                event.licenseSubscriptionId(), event.periodEnd());
        subscriptionService.renewPaid(event.licenseSubscriptionId(), event.stripeSubscriptionId(),
                event.periodStart(), event.periodEnd());
    }

    /**
     * Mark a subscription past due.
     *
     * @param event the failed renewal
     */
    @RabbitListener(queues = PaymentSubscriptionPastDueContract.PAYMENT_SUBSCRIPTION_PAST_DUE_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onSubscriptionPastDue(PaymentSubscriptionPastDueEvent event) {
        log.info("Received payment subscription past due [subscriptionId={}]", event.licenseSubscriptionId());
        subscriptionService.markPastDue(event.licenseSubscriptionId(), event.failedAt());
    }

    /**
     * End a subscription Stripe ended.
     *
     * @param event the end
     */
    @RabbitListener(queues = PaymentSubscriptionEndedContract.PAYMENT_SUBSCRIPTION_ENDED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onSubscriptionEnded(PaymentSubscriptionEndedEvent event) {
        log.info("Received payment subscription ended [subscriptionId={}, reason={}]", event.licenseSubscriptionId(),
                event.reason());
        subscriptionService.endPaid(event.licenseSubscriptionId(), event.endedAt(), event.reason());
    }

    /**
     * Follow a renewal change made on the Stripe side.
     *
     * @param event the change
     */
    @RabbitListener(queues = PaymentSubscriptionRenewalChangedContract.PAYMENT_SUBSCRIPTION_RENEWAL_CHANGED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onRenewalChanged(PaymentSubscriptionRenewalChangedEvent event) {
        log.info("Received payment subscription renewal changed [subscriptionId={}, isAutoRenew={}]",
                event.licenseSubscriptionId(), event.isAutoRenew());
        subscriptionService.syncRenewal(event.licenseSubscriptionId(), event.isAutoRenew());
    }
}
