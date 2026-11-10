package com.triphuddle.amqp.platform;

import java.util.UUID;

/**
 * Published when a currency is disabled.
 *
 * @param currencyId the ID of the currency
 * @param code the ISO 4217 code of the currency
 */
public record CurrencyDisabledEvent(UUID currencyId, String code) {
}
