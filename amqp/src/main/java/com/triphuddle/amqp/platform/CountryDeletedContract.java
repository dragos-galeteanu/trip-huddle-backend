package com.triphuddle.amqp.platform;

/**
 * Queue and dead-letter queue names for {@link CountryDeletedEvent}.
 */
public class CountryDeletedContract {
    public static final String COUNTRY_DELETED_QUEUE_NAME = "platform.country.deleted.queue";
    public static final String COUNTRY_DELETED_DLQ_NAME = "platform.country.deleted.dlq";
}
