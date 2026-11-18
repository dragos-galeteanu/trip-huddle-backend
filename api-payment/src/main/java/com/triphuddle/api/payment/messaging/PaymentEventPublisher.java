package com.triphuddle.api.payment.messaging;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.notification.NotificationEmailContract;
import com.triphuddle.amqp.notification.SendEmailRequest;
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
import com.triphuddle.stripe.subscription.StripeSubscriptionPeriod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes the payment events and emails, each once its transaction commits.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final RabbitMQMessageProducer messageProducer;

    /**
     * Report a subscription's first payment, for api-license to activate it.
     *
     * @param licenseSubscriptionId the ID of the subscription in api-license
     * @param period the Stripe subscription and its paid period
     */
    public void subscriptionActivated(UUID licenseSubscriptionId, StripeSubscriptionPeriod period) {
        publish(new PaymentSubscriptionActivatedEvent(licenseSubscriptionId, period.id(), period.periodStart(),
                period.periodEnd()),
                PaymentSubscriptionActivatedContract.PAYMENT_SUBSCRIPTION_ACTIVATED_QUEUE_NAME, licenseSubscriptionId);
    }

    /**
     * Report a renewal Stripe charged, for api-license to move the subscription on.
     *
     * @param licenseSubscriptionId the ID of the subscription in api-license
     * @param period the Stripe subscription and its paid period
     */
    public void subscriptionRenewed(UUID licenseSubscriptionId, StripeSubscriptionPeriod period) {
        publish(new PaymentSubscriptionRenewedEvent(licenseSubscriptionId, period.id(), period.periodStart(),
                period.periodEnd()),
                PaymentSubscriptionRenewedContract.PAYMENT_SUBSCRIPTION_RENEWED_QUEUE_NAME, licenseSubscriptionId);
    }

    /**
     * Report a renewal that failed while Stripe retries.
     *
     * @param licenseSubscriptionId the ID of the subscription in api-license
     * @param failedAt when the charge failed
     */
    public void subscriptionPastDue(UUID licenseSubscriptionId, Instant failedAt) {
        publish(new PaymentSubscriptionPastDueEvent(licenseSubscriptionId, failedAt),
                PaymentSubscriptionPastDueContract.PAYMENT_SUBSCRIPTION_PAST_DUE_QUEUE_NAME, licenseSubscriptionId);
    }

    /**
     * Report a subscription Stripe ended.
     *
     * @param licenseSubscriptionId the ID of the subscription in api-license
     * @param stripeSubscriptionId the Stripe subscription
     * @param endedAt when it ended
     * @param reason Stripe's reason, or null
     */
    public void subscriptionEnded(UUID licenseSubscriptionId, String stripeSubscriptionId, Instant endedAt,
                                  String reason) {
        publish(new PaymentSubscriptionEndedEvent(licenseSubscriptionId, stripeSubscriptionId, endedAt, reason),
                PaymentSubscriptionEndedContract.PAYMENT_SUBSCRIPTION_ENDED_QUEUE_NAME, licenseSubscriptionId);
    }

    /**
     * Report a renewal change made on the Stripe side.
     *
     * @param licenseSubscriptionId the ID of the subscription in api-license
     * @param isAutoRenew whether it renews at the end of its period
     */
    public void renewalChanged(UUID licenseSubscriptionId, boolean isAutoRenew) {
        publish(new PaymentSubscriptionRenewalChangedEvent(licenseSubscriptionId, isAutoRenew),
                PaymentSubscriptionRenewalChangedContract.PAYMENT_SUBSCRIPTION_RENEWAL_CHANGED_QUEUE_NAME,
                licenseSubscriptionId);
    }

    /**
     * Send an email through notification.
     *
     * @param request the email
     * @param licenseSubscriptionId the subscription it is about, for the log
     */
    public void email(SendEmailRequest request, UUID licenseSubscriptionId) {
        publish(request, NotificationEmailContract.EMAIL_QUEUE_NAME, licenseSubscriptionId);
    }

    // Sent once the transaction commits; a rollback drops it, and the retried Stripe event sends it again.
    private void publish(Object event, String queueName, UUID id) {
        messageProducer.publishAfterCommit(event, "", queueName);
        log.info("Event to publish after commit [queue={}, licenseSubscriptionId={}]", queueName, id);
    }
}
