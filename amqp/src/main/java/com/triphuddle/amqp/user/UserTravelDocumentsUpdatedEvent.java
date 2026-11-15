package com.triphuddle.amqp.user;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Published when a user changes their nationalities or residence permits; carries the whole set, so a consumer
 * keeps the latest one it sees and drops older ones.
 *
 * @param userId the ID of the user
 * @param nationalities the countries whose passports they hold, ISO 3166-1 alpha-2
 * @param residencePermits where they may live besides
 * @param changedAt when the change happened, to order events
 */
public record UserTravelDocumentsUpdatedEvent(
        UUID userId,
        List<String> nationalities,
        List<ResidencePermit> residencePermits,
        Instant changedAt
) {

    /**
     * A right to live in a country.
     *
     * @param countryCode the country, ISO 3166-1 alpha-2
     * @param kind permanent or temporary
     * @param validUntil when it ends, or null if it doesn't
     */
    public record ResidencePermit(String countryCode, ResidencePermitKind kind, LocalDate validUntil) {
    }
}
