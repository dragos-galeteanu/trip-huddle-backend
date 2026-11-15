package com.triphuddle.api.user.messaging;

import com.triphuddle.amqp.RabbitMQMessageProducer;
import com.triphuddle.amqp.license.LicenseSubscriptionActivatedContract;
import com.triphuddle.amqp.license.LicenseSubscriptionActivatedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionCanceledContract;
import com.triphuddle.amqp.license.LicenseSubscriptionCanceledEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionEndedContract;
import com.triphuddle.amqp.license.LicenseSubscriptionEndedEvent;
import com.triphuddle.amqp.license.LicenseSubscriptionRenewalDueContract;
import com.triphuddle.amqp.license.LicenseSubscriptionRenewalDueEvent;
import com.triphuddle.amqp.notification.NotificationEmailContract;
import com.triphuddle.amqp.notification.NotificationEmailType;
import com.triphuddle.amqp.notification.SendEmailRequest;
import com.triphuddle.api.user.entity.User;
import com.triphuddle.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Emails the customer of a license subscription when it starts, is about to renew, is canceled or expires.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LicenseSubscriptionEventListener {

    private static final String GROUP = "GROUP";

    // Dates in UTC, as the periods are worked out in UTC.
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final UserRepository userRepository;
    private final RabbitMQMessageProducer messageProducer;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Tell the customer their subscription is active.
     *
     * @param event the subscription that started
     */
    @RabbitListener(queues = LicenseSubscriptionActivatedContract.LICENSE_SUBSCRIPTION_ACTIVATED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onActivated(LicenseSubscriptionActivatedEvent event) {
        log.info("Received license subscription activated [subscriptionId={}]", event.subscriptionId());
        Map<String, Object> data = new HashMap<>();
        data.put("periodEnd", DATE_FORMAT.format(event.currentPeriodEnd()));
        data.put("isAutoRenew", event.isAutoRenew());
        data.put("isGranted", event.isGranted());
        send(NotificationEmailType.LICENSE_SUBSCRIPTION_ACTIVATED, event.notifyUserId(), event.subscriptionId(),
                event.licenseName(), event.subscriberType(), data);
    }

    /**
     * Remind the customer that their subscription renews soon.
     *
     * @param event the renewal coming up
     */
    @RabbitListener(queues = LicenseSubscriptionRenewalDueContract.LICENSE_SUBSCRIPTION_RENEWAL_DUE_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onRenewalDue(LicenseSubscriptionRenewalDueEvent event) {
        log.info("Received license subscription renewal due [subscriptionId={}]", event.subscriptionId());
        Map<String, Object> data = new HashMap<>();
        data.put("renewsAt", DATE_FORMAT.format(event.renewsAt()));
        data.put("price", formatPrice(event.priceMinor(), event.currency()));
        send(NotificationEmailType.LICENSE_SUBSCRIPTION_RENEWAL_REMINDER, event.notifyUserId(), event.subscriptionId(),
                event.licenseName(), event.subscriberType(), data);
    }

    /**
     * Confirm to the customer that their subscription will not renew.
     *
     * @param event the canceled subscription
     */
    @RabbitListener(queues = LicenseSubscriptionCanceledContract.LICENSE_SUBSCRIPTION_CANCELED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onCanceled(LicenseSubscriptionCanceledEvent event) {
        log.info("Received license subscription canceled [subscriptionId={}]", event.subscriptionId());
        Map<String, Object> data = new HashMap<>();
        data.put("endsAt", DATE_FORMAT.format(event.endsAt()));
        // No actor means the group went away rather than someone canceling.
        data.put("isGroupRemoved", event.actorId() == null);
        send(NotificationEmailType.LICENSE_SUBSCRIPTION_CANCELED, event.notifyUserId(), event.subscriptionId(),
                event.licenseName(), event.subscriberType(), data);
    }

    /**
     * Tell the customer their subscription expired; one ended early needs no email, as its replacement or
     * their own cancel already told them.
     *
     * @param event the subscription that is over
     */
    @RabbitListener(queues = LicenseSubscriptionEndedContract.LICENSE_SUBSCRIPTION_ENDED_QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory")
    public void onEnded(LicenseSubscriptionEndedEvent event) {
        log.info("Received license subscription ended [subscriptionId={}, status={}]", event.subscriptionId(),
                event.status());
        if ("EXPIRED".equals(event.status())) {
            send(NotificationEmailType.LICENSE_SUBSCRIPTION_EXPIRED, event.notifyUserId(), event.subscriptionId(),
                    event.licenseName(), event.subscriberType(), new HashMap<>());
        }
    }

    private void send(NotificationEmailType type, UUID notifyUserId, UUID subscriptionId, String licenseName,
                      String subscriberType, Map<String, Object> data) {
        if (notifyUserId == null) {
            log.info("Nobody to email about license subscription [subscriptionId={}, type={}]", subscriptionId, type);
            return;
        }
        Optional<User> user = userRepository.findByIdAndDeletedAtIsNullAndDisabledAtIsNull(notifyUserId)
                .filter(User::isEmailVerified);
        if (user.isEmpty()) {
            log.info("No verified user to email about license subscription [subscriptionId={}, type={}]",
                    subscriptionId, type);
            return;
        }

        data.put("firstName", user.get().getFirstName());
        data.put("licenseName", licenseName);
        data.put("isGroup", GROUP.equals(subscriberType));
        data.put("subscriptionUrl", "%s/account/subscriptions/%s".formatted(frontendUrl, subscriptionId));
        messageProducer.publish(new SendEmailRequest(type, user.get().getEmail(), data), "",
                NotificationEmailContract.EMAIL_QUEUE_NAME);
        log.info("Event published for license subscription email [subscriptionId={}, type={}, userId={}]",
                subscriptionId, type, notifyUserId);
    }

    // Minor units by the currency's own decimals, so 999 EUR cents is "9.99 EUR".
    private String formatPrice(long priceMinor, String currencyCode) {
        int digits = Math.max(Currency.getInstance(currencyCode).getDefaultFractionDigits(), 0);
        return "%s %s".formatted(BigDecimal.valueOf(priceMinor, digits).toPlainString(), currencyCode);
    }
}
