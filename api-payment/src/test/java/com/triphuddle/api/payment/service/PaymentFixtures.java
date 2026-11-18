package com.triphuddle.api.payment.service;

import com.triphuddle.amqp.license.BillingInterval;
import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.payment.entity.BillingSubscription;
import com.triphuddle.api.payment.entity.BillingSubscriptionStatus;
import com.triphuddle.api.payment.entity.LicensePrice;
import com.triphuddle.api.payment.entity.LicenseProduct;
import com.triphuddle.api.payment.entity.PaymentInvoice;
import com.triphuddle.api.payment.entity.UserContact;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Valid payment records, changed by a test only where it cares.
 */
public final class PaymentFixtures {

    private PaymentFixtures() {
    }

    public static BillingSubscription pending(UUID payer) {
        BillingSubscription subscription = new BillingSubscription();
        subscription.setLicenseSubscriptionId(UUID.randomUUID());
        subscription.setStatus(BillingSubscriptionStatus.PENDING);
        subscription.setLicenseId(UUID.randomUUID());
        subscription.setSubscriberType(LicenseTarget.USER);
        subscription.setSubscriberId(payer);
        subscription.setPurchasedByUserId(payer);
        subscription.setBillingInterval(BillingInterval.MONTH);
        subscription.setListPriceMinor(1000L);
        subscription.setFirstPriceMinor(1000L);
        subscription.setCurrency("EUR");
        subscription.setPendingCreatedAt(Instant.now());
        subscription.setPendingExpiresAt(Instant.now().plus(Duration.ofHours(48)));
        return subscription;
    }

    public static BillingSubscription active(UUID payer, String stripeSubscriptionId) {
        BillingSubscription subscription = pending(payer);
        subscription.setStatus(BillingSubscriptionStatus.ACTIVE);
        subscription.setStripeSubscriptionId(stripeSubscriptionId);
        subscription.setStripeCustomerId("cus_1");
        return subscription;
    }

    public static LicenseProduct product(UUID licenseId, boolean isPublished) {
        LicenseProduct product = new LicenseProduct();
        product.setLicenseId(licenseId);
        product.setCode("PLAN");
        product.setName("Explorer");
        product.setTarget(LicenseTarget.USER);
        product.setLicenseStatus(isPublished ? "PUBLISHED" : "ARCHIVED");
        product.setPriceMinor(1000);
        product.setCurrency("EUR");
        product.setBillingInterval(BillingInterval.MONTH);
        product.setStripeProductId("license_" + licenseId);
        product.setSyncedAt(Instant.now());
        return product;
    }

    public static LicensePrice price(UUID licenseId) {
        LicensePrice price = new LicensePrice();
        price.setLicenseId(licenseId);
        price.setStripePriceId("price_1");
        price.setAmountMinor(1000);
        return price;
    }

    public static UserContact contact(UUID userId, boolean isVerified) {
        UserContact contact = new UserContact();
        contact.setUserId(userId);
        contact.setEmail("payer@example.com");
        contact.setFirstName("Ana");
        contact.setLastName("Pop");
        contact.setEmailVerified(isVerified);
        return contact;
    }

    public static PaymentInvoice invoice(BillingSubscription subscription, long paid) {
        PaymentInvoice invoice = new PaymentInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setStripeInvoiceId("in_1");
        invoice.setLicenseSubscriptionId(subscription.getLicenseSubscriptionId());
        invoice.setSubscriberType(subscription.getSubscriberType());
        invoice.setSubscriberId(subscription.getSubscriberId());
        invoice.setStripeSubscriptionId(subscription.getStripeSubscriptionId());
        invoice.setStripePaymentIntentId("pi_1");
        invoice.setStatus("paid");
        invoice.setCurrency("EUR");
        invoice.setTotalMinor(paid);
        invoice.setAmountPaidMinor(paid);
        return invoice;
    }
}
