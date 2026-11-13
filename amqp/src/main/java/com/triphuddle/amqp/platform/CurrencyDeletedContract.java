package com.triphuddle.amqp.platform;

/**
 * Queue and dead-letter queue names for {@link CurrencyDeletedEvent}.
 */
public class CurrencyDeletedContract {
    public static final String CURRENCY_DELETED_QUEUE_NAME = "platform.currency.deleted.queue";
    public static final String CURRENCY_DELETED_DLQ_NAME = "platform.currency.deleted.dlq";
}
