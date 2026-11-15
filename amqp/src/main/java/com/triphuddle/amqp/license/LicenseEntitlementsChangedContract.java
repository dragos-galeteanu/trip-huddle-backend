package com.triphuddle.amqp.license;

/**
 * Queue and dead-letter queue names for {@link LicenseEntitlementsChangedEvent}.
 */
public class LicenseEntitlementsChangedContract {
    public static final String LICENSE_ENTITLEMENTS_CHANGED_QUEUE_NAME = "license.entitlements.changed.queue";
    public static final String LICENSE_ENTITLEMENTS_CHANGED_DLQ_NAME = "license.entitlements.changed.dlq";
}
