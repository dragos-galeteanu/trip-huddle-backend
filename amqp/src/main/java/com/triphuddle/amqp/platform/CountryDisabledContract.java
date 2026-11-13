package com.triphuddle.amqp.platform;

/**
 * Queue and dead-letter queue names for {@link CountryDisabledEvent}.
 */
public class CountryDisabledContract {
    public static final String COUNTRY_DISABLED_QUEUE_NAME = "platform.country.disabled.queue";
    public static final String COUNTRY_DISABLED_DLQ_NAME = "platform.country.disabled.dlq";
}
