package com.triphuddle.amqp.platform;

import java.util.UUID;

/**
 * Published when a country is deleted.
 *
 * @param countryId the ID of the country
 * @param alpha2 the ISO 3166-1 alpha-2 code of the country
 */
public record CountryDeletedEvent(UUID countryId, String alpha2) {
}
