package com.triphuddle.amqp.platform;

import java.util.UUID;

/**
 * Published when a country is created.
 *
 * @param countryId the ID of the country
 * @param alpha2 the ISO 3166-1 alpha-2 code of the country
 */
public record CountryCreatedEvent(UUID countryId, String alpha2) {
}
