package com.triphuddle.amqp.platform;

/**
 * Queue and dead-letter queue names for {@link CountryCreatedEvent}.
 */
public class CountryCreatedContract {
    public static final String COUNTRY_CREATED_QUEUE_NAME = "platform.country.created.queue";
    public static final String COUNTRY_CREATED_DLQ_NAME = "platform.country.created.dlq";
}
