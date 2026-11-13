package com.triphuddle.amqp.platform;

import java.util.UUID;

/**
 * Published when a currency is deleted.
 *
 * @param currencyId the ID of the currency
 * @param code the ISO 4217 code of the currency
 */
public record CurrencyDeletedEvent(UUID currencyId, String code) {
}
