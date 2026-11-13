package com.triphuddle.api.platform.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response body describing a country.
 *
 * @param id the ID of the country
 * @param alpha2 the ISO 3166-1 alpha-2 code
 * @param alpha3 the ISO 3166-1 alpha-3 code
 * @param numericCode the ISO 3166-1 numeric code
 * @param name the name of the country
 * @param callingCode the international calling code
 * @param standardVatRate the standard VAT rate of the country
 * @param isEuVatMember whether the country is in the EU VAT area
 * @param isEuMember whether the country is an EU member
 * @param isEeaMember whether the country is an EEA member
 * @param isSchengenMember whether the country is in the Schengen area
 * @param defaultCurrencyId the ID of the default currency of the country
 * @param defaultLanguageId the ID of the default language of the country
 * @param createdAt when the country was created
 * @param updatedAt when the country was last updated
 * @param disabledAt when the country was disabled, or null if it is enabled
 * @param deletedAt when the country was deleted, or null if it was not
 */
public record CountryResponse(
        UUID id,
        String alpha2,
        String alpha3,
        String numericCode,
        String name,
        String callingCode,
        BigDecimal standardVatRate,
        boolean isEuVatMember,
        boolean isEuMember,
        boolean isEeaMember,
        boolean isSchengenMember,
        UUID defaultCurrencyId,
        UUID defaultLanguageId,
        Instant createdAt,
        Instant updatedAt,
        Instant disabledAt,
        Instant deletedAt
) {
}
