package com.triphuddle.amqp.platform;

/**
 * Queue and dead-letter queue names for {@link CurrencyCreatedEvent}.
 */
public class CurrencyCreatedContract {
    public static final String CURRENCY_CREATED_QUEUE_NAME = "platform.currency.created.queue";
    public static final String CURRENCY_CREATED_DLQ_NAME = "platform.currency.created.dlq";
}
