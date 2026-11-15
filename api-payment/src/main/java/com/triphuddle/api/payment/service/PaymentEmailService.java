package com.triphuddle.api.payment.service;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.amqp.notification.NotificationEmailType;
import com.triphuddle.amqp.notification.SendEmailRequest;
import com.triphuddle.api.payment.config.PaymentProperties;
import com.triphuddle.api.payment.entity.BillingSubscription;
import com.triphuddle.api.payment.entity.LicenseProduct;
import com.triphuddle.api.payment.entity.UserContact;
import com.triphuddle.api.payment.messaging.PaymentEventPublisher;
import com.triphuddle.api.payment.repository.LicenseProductRepository;
import com.triphuddle.api.payment.repository.UserContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Emails the payer of a subscription about its payments: the user, or for a group the admin who subscribed it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEmailService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final UserContactRepository userContactRepository;
    private final LicenseProductRepository licenseProductRepository;
    private final PaymentEventPublisher eventPublisher;
    private final PaymentProperties paymentProperties;

    /**
     * Tell the payer a charge went through, with a link to its invoice.
     *
     * @param subscription the billing subscription
     * @param amountMinor what was charged, tax included
     * @param currency the ISO 4217 currency code
     * @param invoiceUrl Stripe's hosted invoice page, or null
     */
    public void paymentSucceeded(BillingSubscription subscription, long amountMinor, String currency,
                                 String invoiceUrl) {
        send(NotificationEmailType.LICENSE_PAYMENT_SUCCEEDED, subscription, amountMinor, currency, invoiceUrl, null);
    }

    /**
     * Tell the payer a renewal charge failed, with a link to pay it or change the card.
     *
     * @param subscription the billing subscription
     * @param amountMinor what is due, tax included
     * @param currency the ISO 4217 currency code
     * @param invoiceUrl Stripe's hosted invoice page, where it can be paid
     * @param nextAttemptAt when Stripe tries again, or null if it won't
     */
    public void paymentFailed(BillingSubscription subscription, long amountMinor, String currency, String invoiceUrl,
                              Instant nextAttemptAt) {
        send(NotificationEmailType.LICENSE_PAYMENT_FAILED, subscription, amountMinor, currency, invoiceUrl,
                nextAttemptAt);
    }

    private void send(NotificationEmailType type, BillingSubscription subscription, long amountMinor,
                      String currency, String invoiceUrl, Instant nextAttemptAt) {
        UUID payerUserId = subscription.getSubscriberType() == LicenseTarget.USER
                ? subscription.getSubscriberId() : subscription.getPurchasedByUserId();
        Optional<UserContact> payer = Optional.ofNullable(payerUserId).flatMap(userContactRepository::findByUserId)
                .filter(contact -> contact.getEmail() != null && contact.isEmailVerified()
                        && contact.getDisabledAt() == null && contact.getDeletedAt() == null);
        if (payer.isEmpty()) {
            log.info("No verified payer to email [licenseSubscriptionId={}, type={}]",
                    subscription.getLicenseSubscriptionId(), type);
            return;
        }
        String licenseName = licenseProductRepository.findByLicenseId(subscription.getLicenseId())
                .map(LicenseProduct::getName).orElse("TripHuddle");

        Map<String, Object> data = new HashMap<>();
        data.put("firstName", payer.get().getFirstName());
        data.put("licenseName", licenseName);
        data.put("isGroup", subscription.getSubscriberType() == LicenseTarget.GROUP);
        data.put("amount", formatAmount(amountMinor, currency));
        data.put("invoiceUrl", invoiceUrl);
        data.put("nextAttemptDate", nextAttemptAt == null ? null : DATE_FORMAT.format(nextAttemptAt));
        data.put("subscriptionUrl", paymentProperties.subscriptionUrl()
                .replace("{subscriptionId}", subscription.getLicenseSubscriptionId().toString()));
        eventPublisher.email(new SendEmailRequest(type, payer.get().getEmail(), data),
                subscription.getLicenseSubscriptionId());
        log.info("Payment email queued [licenseSubscriptionId={}, type={}, userId={}]",
                subscription.getLicenseSubscriptionId(), type, payerUserId);
    }

    private static String formatAmount(long amountMinor, String currency) {
        String code = currency.toUpperCase(Locale.ROOT);
        int digits = Math.max(Currency.getInstance(code).getDefaultFractionDigits(), 0);
        return "%s %s".formatted(BigDecimal.valueOf(amountMinor, digits).toPlainString(), code);
    }
}
