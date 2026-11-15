package com.triphuddle.amqp.license;

/**
 * Queue and dead-letter queue names for {@link LicenseCatalogChangedEvent}.
 */
public class LicenseCatalogChangedContract {
    public static final String LICENSE_CATALOG_CHANGED_QUEUE_NAME = "license.catalog.changed.queue";
    public static final String LICENSE_CATALOG_CHANGED_DLQ_NAME = "license.catalog.changed.dlq";
}
