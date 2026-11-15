package com.triphuddle.amqp.license;

import java.util.List;

/** Every queue the license contracts define, declared on the broker by {@code RabbitMQConfig}. */
public class LicenseQueues {
    public static final List<String> QUEUE_NAMES = List.of(
            LicenseSubscriptionActivatedContract.LICENSE_SUBSCRIPTION_ACTIVATED_QUEUE_NAME,
            LicenseSubscriptionActivatedContract.LICENSE_SUBSCRIPTION_ACTIVATED_DLQ_NAME,
            LicenseSubscriptionActivatedContract.LICENSE_SUBSCRIPTION_ACTIVATED_PAYMENT_QUEUE_NAME,
            LicenseSubscriptionActivatedContract.LICENSE_SUBSCRIPTION_ACTIVATED_PAYMENT_DLQ_NAME,
            LicenseSubscriptionRenewedContract.LICENSE_SUBSCRIPTION_RENEWED_QUEUE_NAME,
            LicenseSubscriptionRenewedContract.LICENSE_SUBSCRIPTION_RENEWED_DLQ_NAME,
            LicenseSubscriptionCanceledContract.LICENSE_SUBSCRIPTION_CANCELED_QUEUE_NAME,
            LicenseSubscriptionCanceledContract.LICENSE_SUBSCRIPTION_CANCELED_DLQ_NAME,
            LicenseSubscriptionCanceledContract.LICENSE_SUBSCRIPTION_CANCELED_PAYMENT_QUEUE_NAME,
            LicenseSubscriptionCanceledContract.LICENSE_SUBSCRIPTION_CANCELED_PAYMENT_DLQ_NAME,
            LicenseSubscriptionResumedContract.LICENSE_SUBSCRIPTION_RESUMED_QUEUE_NAME,
            LicenseSubscriptionResumedContract.LICENSE_SUBSCRIPTION_RESUMED_DLQ_NAME,
            LicenseSubscriptionEndedContract.LICENSE_SUBSCRIPTION_ENDED_QUEUE_NAME,
            LicenseSubscriptionEndedContract.LICENSE_SUBSCRIPTION_ENDED_DLQ_NAME,
            LicenseSubscriptionEndedContract.LICENSE_SUBSCRIPTION_ENDED_PAYMENT_QUEUE_NAME,
            LicenseSubscriptionEndedContract.LICENSE_SUBSCRIPTION_ENDED_PAYMENT_DLQ_NAME,
            LicenseSubscriptionRenewalDueContract.LICENSE_SUBSCRIPTION_RENEWAL_DUE_QUEUE_NAME,
            LicenseSubscriptionRenewalDueContract.LICENSE_SUBSCRIPTION_RENEWAL_DUE_DLQ_NAME,
            LicenseEntitlementsChangedContract.LICENSE_ENTITLEMENTS_CHANGED_QUEUE_NAME,
            LicenseEntitlementsChangedContract.LICENSE_ENTITLEMENTS_CHANGED_DLQ_NAME,
            LicenseCatalogChangedContract.LICENSE_CATALOG_CHANGED_QUEUE_NAME,
            LicenseCatalogChangedContract.LICENSE_CATALOG_CHANGED_DLQ_NAME,
            LicenseSubscriptionPendingContract.LICENSE_SUBSCRIPTION_PENDING_QUEUE_NAME,
            LicenseSubscriptionPendingContract.LICENSE_SUBSCRIPTION_PENDING_DLQ_NAME,
            LicenseSubscriptionActivationRefusedContract.LICENSE_SUBSCRIPTION_ACTIVATION_REFUSED_QUEUE_NAME,
            LicenseSubscriptionActivationRefusedContract.LICENSE_SUBSCRIPTION_ACTIVATION_REFUSED_DLQ_NAME
    );
}
