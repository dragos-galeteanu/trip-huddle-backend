package com.triphuddle.amqp.platform;

import java.util.List;

/** Every queue the platform contracts define, declared on the broker by {@code RabbitMQConfig}. */
public class PlatformQueues {
    public static final List<String> QUEUE_NAMES = List.of(
            CountryCreatedContract.COUNTRY_CREATED_QUEUE_NAME,
            CountryCreatedContract.COUNTRY_CREATED_DLQ_NAME,
            CountryDisabledContract.COUNTRY_DISABLED_QUEUE_NAME,
            CountryDisabledContract.COUNTRY_DISABLED_DLQ_NAME,
            CountryDeletedContract.COUNTRY_DELETED_QUEUE_NAME,
            CountryDeletedContract.COUNTRY_DELETED_DLQ_NAME,
            CurrencyCreatedContract.CURRENCY_CREATED_QUEUE_NAME,
            CurrencyCreatedContract.CURRENCY_CREATED_DLQ_NAME,
            CurrencyDisabledContract.CURRENCY_DISABLED_QUEUE_NAME,
            CurrencyDisabledContract.CURRENCY_DISABLED_DLQ_NAME,
            CurrencyDeletedContract.CURRENCY_DELETED_QUEUE_NAME,
            CurrencyDeletedContract.CURRENCY_DELETED_DLQ_NAME,
            LanguageCreatedContract.LANGUAGE_CREATED_QUEUE_NAME,
            LanguageCreatedContract.LANGUAGE_CREATED_DLQ_NAME,
            LanguageDisabledContract.LANGUAGE_DISABLED_QUEUE_NAME,
            LanguageDisabledContract.LANGUAGE_DISABLED_DLQ_NAME,
            LanguageDeletedContract.LANGUAGE_DELETED_QUEUE_NAME,
            LanguageDeletedContract.LANGUAGE_DELETED_DLQ_NAME
    );
}
