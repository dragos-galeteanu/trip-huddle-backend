package com.triphuddle.api.platform.dto;

import com.triphuddle.api.platform.entity.SymbolPosition;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body describing a currency.
 *
 * @param id the ID of the currency
 * @param code the code of the currency
 * @param name the name of the currency
 * @param symbol the symbol of the currency
 * @param symbolPosition whether the symbol goes before or after the amount
 * @param minorUnits the number of decimal places of the currency
 * @param createdAt when the currency was created
 * @param updatedAt when the currency was last updated
 * @param disabledAt when the currency was disabled, or null if it is enabled
 * @param deletedAt when the currency was deleted, or null if it was not
 */
public record CurrencyResponse(
        UUID id,
        String code,
        String name,
        String symbol,
        SymbolPosition symbolPosition,
        int minorUnits,
        Instant createdAt,
        Instant updatedAt,
        Instant disabledAt,
        Instant deletedAt
) {
}
