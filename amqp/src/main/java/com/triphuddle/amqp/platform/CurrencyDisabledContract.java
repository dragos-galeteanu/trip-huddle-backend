package com.triphuddle.amqp.platform;

/**
 * Queue and dead-letter queue names for {@link CurrencyDisabledEvent}.
 */
public class CurrencyDisabledContract {
    public static final String CURRENCY_DISABLED_QUEUE_NAME = "platform.currency.disabled.queue";
    public static final String CURRENCY_DISABLED_DLQ_NAME = "platform.currency.disabled.dlq";
}
